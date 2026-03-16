package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.DocumentRecord;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Pattern PDF_TEXT_PATTERN = Pattern.compile("\\(([^()]*)\\)\\s*Tj");
    private static final Pattern PDF_TEXT_ARRAY_PATTERN = Pattern.compile("\\[(.*?)]\\s*TJ", Pattern.DOTALL);
    private static final Pattern PDF_TEXT_ITEM_PATTERN = Pattern.compile("\\(([^()]*)\\)");
    private static final int CHUNK_SIZE = 480;
    private static final int CHUNK_OVERLAP = 120;

    private final ObjectMapper objectMapper;
    private final SimpleEmbeddingService simpleEmbeddingService;
    private final VectorSearchService vectorSearchService;
    private final DemoContentService demoContentService;

    @Value("${app.document.storage-dir:data/documents}")
    private String storageDir;

    @Value("${app.document.state-file:data/document-state.json}")
    private String stateFile;

    private final Map<String, StoredDocument> documents = new LinkedHashMap<>();
    private final List<StoredChunk> chunks = new ArrayList<>();

    private Path storagePath;
    private Path statePath;

    @PostConstruct
    public void init() {
        storagePath = resolvePath(storageDir);
        statePath = resolvePath(stateFile);

        try {
            Files.createDirectories(storagePath);
            if (statePath.getParent() != null) {
                Files.createDirectories(statePath.getParent());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create document storage directories", e);
        }

        loadState();

        if (documents.isEmpty()) {
            DemoImportResult result = bootstrapDemoDocuments(false);
            log.info("Auto bootstrapped {} demo documents on startup", result.getImported());
        }
    }

    public synchronized DocumentRecord upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        try {
            String originalName = sanitizeFileName(file.getOriginalFilename());
            String contentType = resolveContentType(file);
            String extension = getExtension(originalName);
            String documentId = newDocumentId();
            Path targetPath = storagePath.resolve(buildStoredFileName(documentId, extension));

            file.transferTo(targetPath);
            String extractedText = extractText(targetPath, originalName);
            StoredDocument storedDocument = persistDocument(
                    documentId,
                    originalName,
                    contentType,
                    file.getSize(),
                    targetPath,
                    extractedText
            );
            return toRecord(storedDocument);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
    }

    public synchronized List<DocumentRecord> list() {
        return documents.values().stream()
                .sorted(Comparator.comparing(StoredDocument::getUploadTime).reversed())
                .map(this::toRecord)
                .toList();
    }

    public synchronized int countDocuments() {
        return documents.size();
    }

    public synchronized int countIndexedDocuments() {
        return (int) documents.values().stream()
                .filter(StoredDocument::isIndexed)
                .count();
    }

    public synchronized DemoImportResult bootstrapDemoDocuments(boolean replaceManagedDemoDocuments) {
        Set<String> managedFileNames = demoContentService.getManagedDemoFileNames();
        if (replaceManagedDemoDocuments) {
            List<String> toDelete = documents.values().stream()
                    .filter(document -> managedFileNames.contains(document.getName()))
                    .map(StoredDocument::getId)
                    .toList();
            toDelete.forEach(this::delete);
        }

        int imported = 0;
        int skipped = 0;
        for (DemoContentService.DemoDocument demoDocument : demoContentService.getDemoDocuments()) {
            boolean exists = documents.values().stream()
                    .anyMatch(document -> demoDocument.fileName().equals(document.getName()));
            if (exists) {
                skipped++;
                continue;
            }

            createTextDocument(demoDocument.fileName(), demoDocument.content(), "text/markdown");
            imported++;
        }

        return DemoImportResult.builder()
                .imported(imported)
                .skipped(skipped)
                .totalDocuments(countDocuments())
                .build();
    }

    public synchronized boolean delete(String documentId) {
        StoredDocument removed = documents.remove(documentId);
        if (removed == null) {
            return false;
        }

        List<String> chunkIds = chunks.stream()
                .filter(chunk -> chunk.getDocumentId().equals(documentId))
                .map(StoredChunk::getId)
                .toList();
        chunks.removeIf(chunk -> chunk.getDocumentId().equals(documentId));

        try {
            Files.deleteIfExists(Paths.get(removed.getStoragePath()));
        } catch (IOException e) {
            log.warn("Failed to delete stored file for document {}", documentId, e);
        }

        if (!chunkIds.isEmpty()) {
            vectorSearchService.deleteDocuments(chunkIds);
        }

        persistState();
        return true;
    }

    public synchronized List<ChunkMatch> search(String question, int topK) {
        if (question == null || question.isBlank() || chunks.isEmpty()) {
            return List.of();
        }

        float[] queryVector = simpleEmbeddingService.embed(question);
        List<ChunkMatch> vectorMatches = searchFromVectorStore(queryVector, topK);
        if (!vectorMatches.isEmpty()) {
            return vectorMatches;
        }

        return searchFromLocalChunks(queryVector, topK);
    }

    private Path resolvePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }

        Path applicationDir = new ApplicationHome(DocumentService.class).getDir().toPath().toAbsolutePath();
        return applicationDir.resolve(path).normalize();
    }

    private StoredDocument createTextDocument(String originalName, String content, String contentType) {
        try {
            String documentId = newDocumentId();
            String extension = getExtension(originalName);
            Path targetPath = storagePath.resolve(buildStoredFileName(documentId, extension));
            Files.writeString(targetPath, content, StandardCharsets.UTF_8);
            long size = Files.size(targetPath);
            return persistDocument(documentId, originalName, contentType, size, targetPath, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create demo document " + originalName, e);
        }
    }

    private StoredDocument persistDocument(
            String documentId,
            String originalName,
            String contentType,
            long size,
            Path targetPath,
            String extractedText
    ) {
        List<String> chunkTexts = chunkText(extractedText);
        List<StoredChunk> newChunks = new ArrayList<>();
        List<VectorSearchService.Document> vectorDocuments = new ArrayList<>();

        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunkId = documentId + "-" + i;
            String chunkText = chunkTexts.get(i);
            float[] vector = simpleEmbeddingService.embed(chunkText);

            StoredChunk chunk = StoredChunk.builder()
                    .id(chunkId)
                    .documentId(documentId)
                    .title(originalName)
                    .source(originalName)
                    .content(chunkText)
                    .page(i + 1)
                    .vector(vector)
                    .build();
            newChunks.add(chunk);

            VectorSearchService.Document vectorDocument = new VectorSearchService.Document();
            vectorDocument.setId(chunkId);
            vectorDocument.setTitle(originalName);
            vectorDocument.setContent(chunkText);
            vectorDocument.setSource(originalName);
            vectorDocument.setPage(i + 1);
            vectorDocument.setVector(vector);
            vectorDocuments.add(vectorDocument);
        }

        String message = chunkTexts.isEmpty()
                ? "文档已保存，但未提取到可用于问答的文本内容。"
                : "文档已完成切块并建立检索索引。";

        StoredDocument storedDocument = StoredDocument.builder()
                .id(documentId)
                .name(originalName)
                .contentType(contentType)
                .size(size)
                .uploadTime(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .chunkCount(chunkTexts.size())
                .indexed(!chunkTexts.isEmpty())
                .statusMessage(message)
                .storagePath(targetPath.toString())
                .build();

        documents.put(documentId, storedDocument);
        chunks.addAll(newChunks);
        persistState();

        if (!vectorDocuments.isEmpty()) {
            vectorSearchService.initCollection();
            vectorSearchService.insertDocuments(vectorDocuments);
        }

        return storedDocument;
    }

    private List<ChunkMatch> searchFromVectorStore(float[] queryVector, int topK) {
        List<VectorSearchService.SearchResult> results = vectorSearchService.search(queryVector, topK);
        if (results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .map(result -> ChunkMatch.builder()
                        .documentId(extractDocumentId(result.getId()))
                        .title(result.getTitle())
                        .content(result.getContent())
                        .source(result.getSource())
                        .page(result.getPage())
                        .score(result.getScore())
                        .build())
                .toList();
    }

    private List<ChunkMatch> searchFromLocalChunks(float[] queryVector, int topK) {
        return chunks.stream()
                .map(chunk -> ChunkMatch.builder()
                        .documentId(chunk.getDocumentId())
                        .title(chunk.getTitle())
                        .content(chunk.getContent())
                        .source(chunk.getSource())
                        .page(chunk.getPage())
                        .score(similarity(queryVector, chunk.getVector()))
                        .build())
                .filter(match -> match.getScore() > 0)
                .sorted(Comparator.comparing(ChunkMatch::getScore).reversed())
                .limit(topK)
                .toList();
    }

    private String extractDocumentId(Object chunkId) {
        if (!(chunkId instanceof String value) || value.isBlank()) {
            return null;
        }

        int separatorIndex = value.lastIndexOf('-');
        return separatorIndex > 0 ? value.substring(0, separatorIndex) : value;
    }

    private void loadState() {
        if (!Files.exists(statePath)) {
            return;
        }

        try {
            StateSnapshot snapshot = objectMapper.readValue(statePath.toFile(), StateSnapshot.class);
            documents.clear();
            chunks.clear();

            if (snapshot.getDocuments() != null) {
                for (StoredDocument document : snapshot.getDocuments()) {
                    documents.put(document.getId(), document);
                }
            }

            if (snapshot.getChunks() != null) {
                chunks.addAll(snapshot.getChunks());
            }
        } catch (IOException e) {
            log.warn("Failed to load persisted document state, starting with empty state", e);
            documents.clear();
            chunks.clear();
        }
    }

    private void persistState() {
        StateSnapshot snapshot = new StateSnapshot(new ArrayList<>(documents.values()), new ArrayList<>(chunks));
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath.toFile(), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist document state", e);
        }
    }

    private String extractText(Path filePath, String originalName) {
        String extension = getExtension(originalName).toLowerCase(Locale.ROOT);

        try {
            return switch (extension) {
                case "txt", "md", "csv", "json" -> normalizeText(Files.readString(filePath, StandardCharsets.UTF_8));
                case "docx" -> extractDocxText(filePath);
                case "pdf" -> extractPdfText(filePath);
                default -> "";
            };
        } catch (Exception e) {
            log.warn("Failed to extract text from {}", originalName, e);
            return "";
        }
    }

    private String extractDocxText(Path filePath) {
        try (InputStream inputStream = Files.newInputStream(filePath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                    String text = xml.replace("</w:p>", "\n")
                            .replace("<w:tab/>", "\t")
                            .replaceAll("<[^>]+>", "")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">");
                    return normalizeText(text);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to extract DOCX text from {}", filePath, e);
        }
        return "";
    }

    private String extractPdfText(Path filePath) throws IOException {
        String raw = Files.readString(filePath, StandardCharsets.ISO_8859_1);
        StringBuilder builder = new StringBuilder();

        Matcher matcher = PDF_TEXT_PATTERN.matcher(raw);
        while (matcher.find()) {
            builder.append(decodePdfText(matcher.group(1))).append('\n');
        }

        Matcher arrayMatcher = PDF_TEXT_ARRAY_PATTERN.matcher(raw);
        while (arrayMatcher.find()) {
            Matcher itemMatcher = PDF_TEXT_ITEM_PATTERN.matcher(arrayMatcher.group(1));
            while (itemMatcher.find()) {
                builder.append(decodePdfText(itemMatcher.group(1))).append(' ');
            }
        }

        return normalizeText(builder.toString());
    }

    private String decodePdfText(String text) {
        return text.replace("\\(", "(")
                .replace("\\)", ")")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replace("\\\\", "\\");
    }

    private List<String> chunkText(String text) {
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            if (end < normalized.length()) {
                int boundary = normalized.lastIndexOf(' ', end);
                if (boundary > start + 120) {
                    end = boundary;
                }
            }

            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                result.add(chunk);
            }

            if (end >= normalized.length()) {
                break;
            }

            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }

        return result;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\uFEFF", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private float similarity(float[] left, float[] right) {
        float score = 0;
        int size = Math.min(left.length, right.length);
        for (int i = 0; i < size; i++) {
            score += left[i] * right[i];
        }
        return score;
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document.txt";
        }

        String fileName = Paths.get(originalFilename).getFileName().toString();
        return fileName.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1);
    }

    private String resolveContentType(MultipartFile file) {
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            return file.getContentType();
        }

        String extension = getExtension(sanitizeFileName(file.getOriginalFilename())).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "txt" -> MediaType.TEXT_PLAIN_VALUE;
            case "md" -> "text/markdown";
            case "csv" -> "text/csv";
            case "json" -> MediaType.APPLICATION_JSON_VALUE;
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private String newDocumentId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String buildStoredFileName(String documentId, String extension) {
        return documentId + (extension.isBlank() ? "" : "." + extension);
    }

    private DocumentRecord toRecord(StoredDocument document) {
        return DocumentRecord.builder()
                .id(document.getId())
                .name(document.getName())
                .size(document.getSize())
                .contentType(document.getContentType())
                .uploadTime(document.getUploadTime())
                .chunkCount(document.getChunkCount())
                .indexed(document.isIndexed())
                .statusMessage(document.getStatusMessage())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkMatch {
        private String documentId;
        private String title;
        private String content;
        private String source;
        private Integer page;
        private Float score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DemoImportResult {
        private int imported;
        private int skipped;
        private int totalDocuments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StoredDocument {
        private String id;
        private String name;
        private long size;
        private String contentType;
        private String uploadTime;
        private int chunkCount;
        private boolean indexed;
        private String statusMessage;
        private String storagePath;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StoredChunk {
        private String id;
        private String documentId;
        private String title;
        private String source;
        private String content;
        private Integer page;
        private float[] vector;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StateSnapshot {
        private List<StoredDocument> documents = new ArrayList<>();
        private List<StoredChunk> chunks = new ArrayList<>();
    }
}

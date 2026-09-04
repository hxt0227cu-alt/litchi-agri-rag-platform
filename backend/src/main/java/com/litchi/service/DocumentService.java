package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.DocumentRecord;
import com.litchi.dto.PageResponse;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private static final int CHUNK_SIZE = 480;
    private static final int CHUNK_OVERLAP = 120;
    private final ObjectMapper objectMapper;
    private final MysqlStateStoreService mysqlStateStoreService;
    private final SimpleEmbeddingService simpleEmbeddingService;
    private final VectorSearchService vectorSearchService;
    private final DemoContentService demoContentService;
    private final BM25Scorer bm25Scorer;

    @Value("${app.retrieval.strategy:hybrid}")
    private String retrievalStrategy;

    @Value("${app.document.storage-dir:data/documents}")
    private String storageDir;

    @Value("${app.document.state-file:data/document-state.json}")
    private String stateFile;

    @Value("${app.document.config-file:data/document-config.json}")
    private String configFile;

    private final Map<String, StoredDocument> documents = new LinkedHashMap<>();
    private final List<StoredChunk> chunks = new CopyOnWriteArrayList<>();

    private Path configPath;
    private Path storagePath;
    private Path statePath;

    @PostConstruct
    public void init() {
        configPath = resolvePath(configFile);
        applyConfiguredStorageOverrides();
        storagePath = resolvePath(storageDir);
        statePath = resolvePath(stateFile);

        try {
            ensureStorageDirectories(storagePath, statePath);
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
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
        return upload(file, null, null, null);
    }

    public synchronized DocumentRecord upload(MultipartFile file, String title, String ownerId, String ownerUsername) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        try {
            String originalName = sanitizeFileName(file.getOriginalFilename());
            String contentType = resolveContentType(file);
            String extension = getExtension(originalName);
            String documentId = newDocumentId();
            Path targetPath = storagePath.resolve(buildStoredFileName(documentId, extension));
            String displayTitle = title == null || title.isBlank() ? originalName : title.trim();

            file.transferTo(targetPath);
            String extractedText = extractText(targetPath, originalName);
            StoredDocument storedDocument = persistDocument(
                    documentId,
                    originalName,
                    displayTitle,
                    contentType,
                    file.getSize(),
                    targetPath,
                    extractedText,
                    ownerId,
                    ownerUsername
            );
            return toRecord(storedDocument);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
    }

    public synchronized List<DocumentRecord> list() {
        return list(null, 1, Integer.MAX_VALUE).getItems();
    }

    public synchronized PageResponse<DocumentRecord> list(String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        return documents.values().stream()
                .filter(document -> matchesKeyword(document, keyword))
                .sorted(Comparator.comparing(StoredDocument::getUploadTime).reversed())
                .map(this::toRecord)
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), items -> {
                    int fromIndex = Math.min((safePage - 1) * safeSize, items.size());
                    int toIndex = Math.min(fromIndex + safeSize, items.size());
                    return PageResponse.<DocumentRecord>builder()
                            .total(items.size())
                            .page(safePage)
                            .size(safeSize)
                            .items(items.subList(fromIndex, toIndex))
                            .build();
                }));
    }

    public synchronized int countDocuments() {
        return documents.size();
    }

    public synchronized int countIndexedDocuments() {
        return (int) documents.values().stream()
                .filter(StoredDocument::isIndexed)
                .count();
    }

    public synchronized StorageSettings getStorageSettings() {
        return new StorageSettings(storagePath.toString(), statePath.toString());
    }

    public synchronized StorageSettings updateStorageSettings(String requestedStorageDir, String requestedStateFile) {
        String nextStorageDir = requestedStorageDir == null || requestedStorageDir.isBlank()
                ? storagePath.toString()
                : requestedStorageDir.trim();
        String nextStateFile = requestedStateFile == null || requestedStateFile.isBlank()
                ? statePath.toString()
                : requestedStateFile.trim();

        Path nextStoragePath = resolvePath(nextStorageDir);
        Path nextStatePath = resolvePath(nextStateFile);

        try {
            ensureStorageDirectories(nextStoragePath, nextStatePath);
            moveManagedDocuments(nextStoragePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update document storage paths", e);
        }

        storageDir = nextStoragePath.toString();
        stateFile = nextStatePath.toString();
        storagePath = nextStoragePath;
        statePath = nextStatePath;

        persistStorageConfig();
        persistState();
        return getStorageSettings();
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

            createTextDocument(demoDocument.fileName(), demoDocument.title(), demoDocument.content(), "text/markdown");
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

    public synchronized int syncVectorIndex() {
        if (chunks.isEmpty()) {
            return 0;
        }

        List<VectorSearchService.Document> vectorDocuments = chunks.stream()
                .map(this::toVectorDocument)
                .toList();
        return upsertVectorDocuments(vectorDocuments) ? vectorDocuments.size() : 0;
    }

    public List<ChunkMatch> search(String question, int topK) {
        if (question == null || question.isBlank() || chunks.isEmpty()) {
            return List.of();
        }

        int candidateLimit = Math.max(topK * 3, 8);
        float[] queryVector = simpleEmbeddingService.embed(question);
        List<ChunkMatch> vectorMatches = searchFromVectorStore(queryVector, candidateLimit);
        if (vectorMatches.isEmpty()) {
            return rerankMatches(question, searchFromLocalChunks(question, queryVector, candidateLimit), topK);
        }

        List<ChunkMatch> merged = mergeCleanMatches(
                vectorMatches,
                searchFromLocalChunks(question, queryVector, candidateLimit),
                candidateLimit
        );
        return rerankMatches(question, merged, topK);
    }

    private Path resolvePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }

        Path applicationDir = new ApplicationHome(DocumentService.class).getDir().toPath().toAbsolutePath();
        return applicationDir.resolve(path).normalize();
    }

    private void applyConfiguredStorageOverrides() {
        if (!Files.exists(configPath)) {
            return;
        }

        try {
            DocumentStorageConfig config = objectMapper.readValue(configPath.toFile(), DocumentStorageConfig.class);
            if (config.getStorageDir() != null && !config.getStorageDir().isBlank()) {
                storageDir = config.getStorageDir().trim();
            }
            if (config.getStateFile() != null && !config.getStateFile().isBlank()) {
                stateFile = config.getStateFile().trim();
            }
        } catch (IOException e) {
            log.warn("Failed to load persisted document storage config, using application defaults", e);
        }
    }

    private void ensureStorageDirectories(Path nextStoragePath, Path nextStatePath) throws IOException {
        Files.createDirectories(nextStoragePath);
        if (nextStatePath.getParent() != null) {
            Files.createDirectories(nextStatePath.getParent());
        }
    }

    private void moveManagedDocuments(Path nextStoragePath) throws IOException {
        for (StoredDocument document : documents.values()) {
            String extension = getExtension(document.getName());
            Path currentPath = Paths.get(document.getStoragePath()).normalize();
            Path nextPath = nextStoragePath.resolve(buildStoredFileName(document.getId(), extension)).normalize();
            if (currentPath.equals(nextPath)) {
                continue;
            }

            if (Files.exists(currentPath)) {
                Files.move(currentPath, nextPath, StandardCopyOption.REPLACE_EXISTING);
            } else if (!Files.exists(nextPath)) {
                log.warn("Document file {} does not exist while migrating storage path", currentPath);
            }
            document.setStoragePath(nextPath.toString());
        }
    }

    private void persistStorageConfig() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    configPath.toFile(),
                    new DocumentStorageConfig(storagePath.toString(), statePath.toString())
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist document storage config", e);
        }
    }

    private StoredDocument createTextDocument(String originalName, String title, String content, String contentType) {
        try {
            String documentId = newDocumentId();
            String extension = getExtension(originalName);
            Path targetPath = storagePath.resolve(buildStoredFileName(documentId, extension));
            Files.writeString(targetPath, content, StandardCharsets.UTF_8);
            long size = Files.size(targetPath);
            return persistDocument(documentId, originalName, title, contentType, size, targetPath, content, null, null);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create demo document " + originalName, e);
        }
    }

    private StoredDocument persistDocument(
            String documentId,
            String originalName,
            String title,
            String contentType,
            long size,
            Path targetPath,
            String extractedText,
            String ownerId,
            String ownerUsername
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
                .title(title)
                .contentType(contentType)
                .size(size)
                .uploadTime(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .chunkCount(chunkTexts.size())
                .indexed(!chunkTexts.isEmpty())
                .statusMessage(message)
                .storagePath(targetPath.toString())
                .ownerId(ownerId)
                .ownerUsername(ownerUsername)
                .build();

        documents.put(documentId, storedDocument);
        chunks.addAll(newChunks);
        persistState();

        upsertVectorDocuments(vectorDocuments);

        return storedDocument;
    }

    private List<ChunkMatch> searchFromVectorStore(float[] queryVector, int topK) {
        List<VectorSearchService.SearchResult> results = vectorSearchService.search(queryVector, topK);
        if (results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .map(this::toChunkMatch)
                .filter(Objects::nonNull)
                .filter(match -> !isUnusableMatch(match))
                .toList();
    }

    private ChunkMatch toChunkMatch(VectorSearchService.SearchResult result) {
        ChunkMatch match = ChunkMatch.builder()
                .documentId(extractDocumentId(result.getId()))
                .title(result.getTitle())
                .content(result.getContent())
                .source(result.getSource())
                .page(result.getPage())
                .score(result.getScore())
                .build();

        if (!looksGarbled(match.getContent()) && !looksGarbled(match.getTitle())) {
            return match;
        }

        StoredChunk localChunk = findLocalChunk(match);
        if (localChunk != null) {
            return ChunkMatch.builder()
                    .documentId(localChunk.getDocumentId())
                    .title(localChunk.getTitle())
                    .content(localChunk.getContent())
                    .source(localChunk.getSource())
                    .page(localChunk.getPage())
                    .score(match.getScore())
                    .build();
        }

        String repairedTitle = repairMojibake(match.getTitle());
        String repairedContent = repairMojibake(match.getContent());
        if (looksGarbled(repairedTitle) || looksGarbled(repairedContent)) {
            log.warn("Discarding garbled vector result from source={}, page={}", match.getSource(), match.getPage());
            return null;
        }

        return ChunkMatch.builder()
                .documentId(match.getDocumentId())
                .title(repairedTitle)
                .content(repairedContent)
                .source(match.getSource())
                .page(match.getPage())
                .score(match.getScore())
                .build();
    }

    private List<ChunkMatch> mergeCleanMatches(List<ChunkMatch> primary, List<ChunkMatch> fallback, int topK) {
        List<ChunkMatch> merged = new ArrayList<>();
        Set<String> seenKeys = new java.util.LinkedHashSet<>();
        addCleanMatches(merged, seenKeys, primary, topK);
        addCleanMatches(merged, seenKeys, fallback, topK);
        return merged;
    }

    private void addCleanMatches(List<ChunkMatch> target, Set<String> seenKeys, List<ChunkMatch> candidates, int topK) {
        for (ChunkMatch match : candidates) {
            if (target.size() >= topK) {
                return;
            }
            if (isUnusableMatch(match)) {
                continue;
            }

            String key = matchKey(match);
            if (seenKeys.add(key)) {
                target.add(match);
            }
        }
    }

    private boolean isUnusableMatch(ChunkMatch match) {
        return match == null
                || match.getContent() == null
                || match.getContent().isBlank()
                || looksGarbled(match.getTitle())
                || looksGarbled(match.getContent());
    }

    private String matchKey(ChunkMatch match) {
        String content = match.getContent() == null
                ? ""
                : match.getContent().substring(0, Math.min(64, match.getContent().length()));
        return String.join("|",
                normalizeComparable(match.getDocumentId()),
                normalizeComparable(match.getSource()),
                normalizeComparable(match.getTitle()),
                String.valueOf(match.getPage()),
                content);
    }

    private StoredChunk findLocalChunk(ChunkMatch match) {
        if (match == null) {
            return null;
        }

        String documentId = match.getDocumentId();
        Integer page = match.getPage();
        if (documentId != null && !documentId.isBlank()) {
            java.util.Optional<StoredChunk> byDocumentId = chunks.stream()
                    .filter(chunk -> documentId.equals(chunk.getDocumentId()))
                    .filter(chunk -> page == null || page.equals(chunk.getPage()))
                    .findFirst();
            if (byDocumentId.isPresent()) {
                return byDocumentId.get();
            }
        }

        String source = normalizeComparable(match.getSource());
        if (!source.isBlank()) {
            java.util.Optional<StoredChunk> bySource = chunks.stream()
                    .filter(chunk -> source.equals(normalizeComparable(chunk.getSource())))
                    .filter(chunk -> page == null || page.equals(chunk.getPage()))
                    .findFirst();
            if (bySource.isPresent()) {
                return bySource.get();
            }
        }

        String title = normalizeComparable(match.getTitle());
        if (!title.isBlank()) {
            return chunks.stream()
                    .filter(chunk -> title.equals(normalizeComparable(chunk.getTitle())))
                    .filter(chunk -> page == null || page.equals(chunk.getPage()))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private boolean looksGarbled(String text) {
        return mojibakeScore(text) >= 3;
    }

    private int mojibakeScore(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int score = 0;
        if (text.contains("�")) {
            score += 3;
        }
        String[] markers = {
                "鑽", "灊", "鐐", "柦", "鐥", "槻", "娌", "銆", "锛", "濡", "鍥", "鏂", "闂", "鍙", "鍦", "骞"
        };
        for (String marker : markers) {
            if (text.contains(marker)) {
                score++;
            }
        }
        return score;
    }

    private String repairMojibake(String text) {
        if (!looksGarbled(text)) {
            return text;
        }

        try {
            String repaired = new String(text.getBytes(Charset.forName("GB18030")), StandardCharsets.UTF_8);
            return mojibakeScore(repaired) < mojibakeScore(text) ? repaired : text;
        } catch (Exception e) {
            return text;
        }
    }

    private String normalizeComparable(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<ChunkMatch> searchFromLocalChunks(String question, float[] queryVector, int topK) {
        String strategy = normalizeStrategy();
        if ("lexical".equals(strategy)) {
            return lexicalLocalSearch(question, queryVector, topK);
        }

        List<String> searchableDocs = chunks.stream().map(this::searchableText).toList();
        BM25Scorer.CorpusStats bm25Stats = bm25Scorer.buildStats(searchableDocs);
        int size = chunks.size();
        double[] bm25Scores = new double[size];
        float[] vectorScores = new float[size];
        for (int i = 0; i < size; i++) {
            bm25Scores[i] = bm25Scorer.score(question, searchableDocs.get(i), bm25Stats);
            vectorScores[i] = similarity(queryVector, chunks.get(i).getVector());
        }

        List<ChunkMatch> matches = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            StoredChunk chunk = chunks.get(i);
            float score = fusedScore(strategy, question, chunk, vectorScores[i], bm25Scores[i]);
            matches.add(ChunkMatch.builder()
                    .documentId(chunk.getDocumentId())
                    .title(chunk.getTitle())
                    .content(chunk.getContent())
                    .source(chunk.getSource())
                    .page(chunk.getPage())
                    .score(score)
                    .build());
        }
        return matches.stream()
                .filter(match -> !isUnusableMatch(match))
                .filter(match -> match.getScore() > 0)
                .sorted(Comparator.comparing(ChunkMatch::getScore).reversed())
                .limit(topK)
                .toList();
    }

    private String normalizeStrategy() {
        return retrievalStrategy == null ? "hybrid" : retrievalStrategy.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Searchable text for BM25. The source name and title are repeated so that
     * a query term hitting the filename or title contributes more term
     * frequency than a plain body occurrence.
     */
    private String searchableText(StoredChunk chunk) {
        String source = chunk.getSource() == null ? "" : chunk.getSource();
        String title = chunk.getTitle() == null ? "" : chunk.getTitle();
        return source + " " + title + " " + source + " " + title + " " + chunk.getContent();
    }

    /** Combines the configured retrieval signals into a comparable score. */
    private float fusedScore(String strategy, String question, StoredChunk chunk, float vectorScore, double bm25Score) {
        float lexical = lexicalBoost(question, chunk);
        return switch (strategy) {
            case "bm25" -> (float) BM25Scorer.normalize(bm25Score);
            case "vector" -> vectorScore;
            default -> vectorScore + lexical + (float) BM25Scorer.normalize(bm25Score);
        };
    }

    /** Legacy lexical retrieval: cosine similarity plus domain-term boost, no BM25. */
    private List<ChunkMatch> lexicalLocalSearch(String question, float[] queryVector, int topK) {
        return chunks.stream()
                .map(chunk -> ChunkMatch.builder()
                        .documentId(chunk.getDocumentId())
                        .title(chunk.getTitle())
                        .content(chunk.getContent())
                        .source(chunk.getSource())
                        .page(chunk.getPage())
                        .score(similarity(queryVector, chunk.getVector()) + lexicalBoost(question, chunk))
                        .build())
                .filter(match -> !isUnusableMatch(match))
                .filter(match -> match.getScore() > 0)
                .sorted(Comparator.comparing(ChunkMatch::getScore).reversed())
                .limit(topK)
                .toList();
    }


    private List<ChunkMatch> rerankMatches(String question, List<ChunkMatch> matches, int topK) {
        List<ChunkMatch> ranked = matches.stream()
                .filter(match -> !isUnusableMatch(match))
                .sorted(Comparator.comparingDouble((ChunkMatch match) -> rerankScore(question, match)).reversed())
                .toList();

        List<ChunkMatch> selected = new ArrayList<>();
        Set<String> selectedKeys = new java.util.LinkedHashSet<>();
        Set<String> selectedSources = new java.util.LinkedHashSet<>();

        for (ChunkMatch match : ranked) {
            if (selected.size() >= topK) {
                break;
            }
            String source = normalizeComparable(match.getSource());
            if (!source.isBlank() && selectedSources.add(source) && selectedKeys.add(matchKey(match))) {
                selected.add(match);
            }
        }

        for (ChunkMatch match : ranked) {
            if (selected.size() >= topK) {
                break;
            }
            if (selectedKeys.add(matchKey(match))) {
                selected.add(match);
            }
        }

        return selected;
    }

    private double rerankScore(String question, ChunkMatch match) {
        double baseScore = match.getScore() == null ? 0 : match.getScore();
        String title = normalizeComparable(match.getTitle());
        String source = normalizeComparable(match.getSource());
        double titleBoost = 0;
        for (String term : extractSearchTerms(question)) {
            if (title.contains(normalizeComparable(term)) || source.contains(normalizeComparable(term))) {
                titleBoost += 0.55;
            }
        }
        return baseScore + titleBoost;
    }

    private float lexicalBoost(String question, StoredChunk chunk) {
        List<String> terms = extractSearchTerms(question);
        if (terms.isEmpty()) {
            return 0F;
        }

        String title = normalizeComparable(chunk.getTitle());
        String source = normalizeComparable(chunk.getSource());
        String content = normalizeComparable(chunk.getContent());

        float boost = 0F;
        for (String term : terms) {
            String normalizedTerm = normalizeComparable(term);
            if (normalizedTerm.isBlank()) {
                continue;
            }
            if (title.contains(normalizedTerm) || source.contains(normalizedTerm)) {
                boost += 0.45F;
            }
            if (content.contains(normalizedTerm)) {
                boost += 0.22F;
            }
        }
        return boost;
    }

    private List<String> extractSearchTerms(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        List<String> keywords = new ArrayList<>();
        String normalized = question.trim();
        String[] domainTerms = {
                "炭疽病", "炭疽", "霜疫霉病", "霜疫病", "霜疫", "桂味", "妃子笑", "花果期", "花穗期",
                "幼果期", "雨季", "阴雨", "蒂蛀虫", "荔枝蝽", "冬季清园", "清园", "保果", "巡园", "绿色", "监测"
        };
        for (String domainTerm : domainTerms) {
            if (normalized.contains(domainTerm)) {
                keywords.add(domainTerm);
            }
        }
        return keywords.stream().distinct().toList();
    }

    private String extractDocumentId(Object chunkId) {
        if (!(chunkId instanceof String value) || value.isBlank()) {
            return null;
        }

        int separatorIndex = value.lastIndexOf('-');
        return separatorIndex > 0 ? value.substring(0, separatorIndex) : value;
    }

    private boolean upsertVectorDocuments(List<VectorSearchService.Document> vectorDocuments) {
        if (vectorDocuments == null || vectorDocuments.isEmpty()) {
            return true;
        }

        if (!vectorSearchService.initCollection()) {
            log.warn("Skipping vector synchronization because Milvus collection is unavailable");
            return false;
        }

        List<String> ids = vectorDocuments.stream()
                .map(VectorSearchService.Document::getId)
                .toList();
        vectorSearchService.deleteDocuments(ids);
        vectorSearchService.insertDocuments(vectorDocuments);
        return true;
    }

    private VectorSearchService.Document toVectorDocument(StoredChunk chunk) {
        VectorSearchService.Document vectorDocument = new VectorSearchService.Document();
        vectorDocument.setId(chunk.getId());
        vectorDocument.setTitle(chunk.getTitle());
        vectorDocument.setContent(chunk.getContent());
        vectorDocument.setSource(chunk.getSource());
        vectorDocument.setPage(chunk.getPage());
        vectorDocument.setVector(chunk.getVector());
        return vectorDocument;
    }

    private void loadState() {
        if (Files.exists(statePath)) {
            try {
                applyState(objectMapper.readValue(statePath.toFile(), StateSnapshot.class));
            } catch (IOException e) {
                log.warn("Failed to load persisted document state, starting with empty state", e);
                documents.clear();
                chunks.clear();
            }
        }

        if (!mysqlStateStoreService.isActive()) {
            return;
        }

        java.util.Optional<MysqlStateStoreService.DocumentStateData> mysqlState = mysqlStateStoreService.loadDocumentState();
        if (mysqlState.isPresent()) {
            applyState(fromMysqlState(mysqlState.get()));
            persistLocalState();
            return;
        }

        if (!documents.isEmpty() || !chunks.isEmpty()) {
            mysqlStateStoreService.saveDocumentState(
                    toMysqlState(new StateSnapshot(new ArrayList<>(documents.values()), new ArrayList<>(chunks)))
            );
        }
    }

    private void persistState() {
        StateSnapshot snapshot = new StateSnapshot(new ArrayList<>(documents.values()), new ArrayList<>(chunks));
        persistLocalState(snapshot);
        mysqlStateStoreService.saveDocumentState(toMysqlState(snapshot));
    }

    private void persistLocalState() {
        persistLocalState(new StateSnapshot(new ArrayList<>(documents.values()), new ArrayList<>(chunks)));
    }

    private void persistLocalState(StateSnapshot snapshot) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath.toFile(), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist document state", e);
        }
    }

    private void applyState(StateSnapshot snapshot) {
        documents.clear();
        chunks.clear();

        if (snapshot == null) {
            return;
        }

        if (snapshot.getDocuments() != null) {
            for (StoredDocument document : snapshot.getDocuments()) {
                StoredDocument normalized = normalizeDocument(document);
                documents.put(normalized.getId(), normalized);
            }
        }

        if (snapshot.getChunks() != null) {
            chunks.addAll(snapshot.getChunks());
        }
    }

    private MysqlStateStoreService.DocumentStateData toMysqlState(StateSnapshot snapshot) {
        List<MysqlStateStoreService.DocumentData> mysqlDocuments = snapshot.getDocuments() == null
                ? List.of()
                : snapshot.getDocuments().stream()
                .map(document -> new MysqlStateStoreService.DocumentData(
                        document.getId(),
                        document.getName(),
                        resolveDocumentTitle(document),
                        document.getSize(),
                        document.getContentType(),
                        document.getUploadTime(),
                        document.getChunkCount(),
                        document.isIndexed(),
                        document.getStatusMessage(),
                        document.getStoragePath(),
                        document.getOwnerId(),
                        document.getOwnerUsername()
                ))
                .toList();

        List<MysqlStateStoreService.DocumentChunkData> mysqlChunks = snapshot.getChunks() == null
                ? List.of()
                : snapshot.getChunks().stream()
                .map(chunk -> new MysqlStateStoreService.DocumentChunkData(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getTitle(),
                        chunk.getSource(),
                        chunk.getContent(),
                        chunk.getPage(),
                        chunk.getVector()
                ))
                .toList();

        return new MysqlStateStoreService.DocumentStateData(mysqlDocuments, mysqlChunks);
    }

    private StateSnapshot fromMysqlState(MysqlStateStoreService.DocumentStateData state) {
        List<StoredDocument> mysqlDocuments = state.getDocuments() == null
                ? List.of()
                : state.getDocuments().stream()
                .map(document -> StoredDocument.builder()
                        .id(document.getId())
                        .name(document.getName())
                        .title(document.getTitle())
                        .size(document.getSize())
                        .contentType(document.getContentType())
                        .uploadTime(document.getUploadTime())
                        .chunkCount(document.getChunkCount())
                        .indexed(document.isIndexed())
                        .statusMessage(document.getStatusMessage())
                        .storagePath(document.getStoragePath())
                        .ownerId(document.getOwnerId())
                        .ownerUsername(document.getOwnerUsername())
                        .build())
                .toList();

        List<StoredChunk> mysqlChunks = state.getChunks() == null
                ? List.of()
                : state.getChunks().stream()
                .map(chunk -> StoredChunk.builder()
                        .id(chunk.getId())
                        .documentId(chunk.getDocumentId())
                        .title(chunk.getTitle())
                        .source(chunk.getSource())
                        .content(chunk.getContent())
                        .page(chunk.getPage())
                        .vector(chunk.getVector())
                        .build())
                .toList();

        return new StateSnapshot(new ArrayList<>(mysqlDocuments), new ArrayList<>(mysqlChunks));
    }

    private StoredDocument normalizeDocument(StoredDocument document) {
        if (document == null) {
            return null;
        }
        return StoredDocument.builder()
                .id(document.getId())
                .name(document.getName())
                .title(resolveDocumentTitle(document))
                .size(document.getSize())
                .contentType(document.getContentType())
                .uploadTime(document.getUploadTime())
                .chunkCount(document.getChunkCount())
                .indexed(document.isIndexed())
                .statusMessage(document.getStatusMessage())
                .storagePath(document.getStoragePath())
                .ownerId(document.getOwnerId())
                .ownerUsername(document.getOwnerUsername())
                .build();
    }

    private String resolveDocumentTitle(StoredDocument document) {
        if (document == null) {
            return "";
        }
        if (document.getTitle() != null && !document.getTitle().isBlank()) {
            return document.getTitle();
        }
        return document.getName() == null ? "" : document.getName();
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
             XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder builder = new StringBuilder();
            for (var paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    builder.append(text).append('\n');
                }
            }
            for (var table : document.getTables()) {
                table.getRows().forEach(row -> {
                    row.getTableCells().forEach(cell -> {
                        String text = cell.getText();
                        if (text != null && !text.isBlank()) {
                            builder.append(text).append('\n');
                        }
                    });
                });
            }
            return normalizeText(builder.toString());
        } catch (IOException e) {
            log.warn("Failed to extract DOCX text from {}", filePath, e);
        }
        return "";
    }

    private String extractPdfText(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            if (document.isEncrypted()) {
                log.warn("PDF {} is encrypted and cannot be indexed", filePath);
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return normalizeText(stripper.getText(document));
        }
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

    private boolean matchesKeyword(StoredDocument document, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return document.getName().toLowerCase(Locale.ROOT).contains(needle)
                || (document.getTitle() != null && document.getTitle().toLowerCase(Locale.ROOT).contains(needle))
                || (document.getOwnerUsername() != null && document.getOwnerUsername().toLowerCase(Locale.ROOT).contains(needle));
    }

    private String buildStoredFileName(String documentId, String extension) {
        return documentId + (extension.isBlank() ? "" : "." + extension);
    }

    private DocumentRecord toRecord(StoredDocument document) {
        return DocumentRecord.builder()
                .id(document.getId())
                .name(document.getName())
                .title(resolveDocumentTitle(document))
                .size(document.getSize())
                .contentType(document.getContentType())
                .uploadTime(document.getUploadTime())
                .chunkCount(document.getChunkCount())
                .indexed(document.isIndexed())
                .statusMessage(document.getStatusMessage())
                .ownerId(document.getOwnerId())
                .ownerUsername(document.getOwnerUsername())
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
        private String title;
        private long size;
        private String contentType;
        private String uploadTime;
        private int chunkCount;
        private boolean indexed;
        private String statusMessage;
        private String storagePath;
        private String ownerId;
        private String ownerUsername;
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
    private static class DocumentStorageConfig {
        private String storageDir;
        private String stateFile;
    }

    public record StorageSettings(String documentStorageDir, String documentStateFile) {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StateSnapshot {
        private List<StoredDocument> documents = new ArrayList<>();
        private List<StoredChunk> chunks = new ArrayList<>();
    }
}

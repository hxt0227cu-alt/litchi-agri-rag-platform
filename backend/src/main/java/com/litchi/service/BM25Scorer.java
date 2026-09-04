package com.litchi.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic BM25 (Best Matching 25) lexical scorer over local chunks.
 *
 * <p>Unlike the hash-based {@link SimpleEmbeddingService} vectors, BM25 gives a
 * principled, explainable lexical ranking: term frequency is saturated with
 * {@code k1}, document length is normalised with {@code b} against the corpus
 * average, and rare terms get more weight through inverse document frequency.
 * Together with the vector recall it forms the hybrid retrieval strategy in
 * {@link DocumentService}.</p>
 *
 * <p>The tokenizer mirrors the embedding tokenizer (character bigrams plus
 * whole words) so both retrieval signals are computed over the same units.</p>
 */
@Component
public class BM25Scorer {

    /** Term-frequency saturation factor (classic BM25 default). */
    public static final double K1 = 1.5;

    /** Length normalisation factor (classic BM25 default). */
    public static final double B = 0.75;

    public record CorpusStats(double avgDocLength, Map<String, Double> idf) {
    }

    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return tokens;
        }

        String compact = normalized.replace(" ", "");
        for (int i = 0; i + 2 <= compact.length(); i++) {
            tokens.add(compact.substring(i, i + 2));
        }

        for (String word : normalized.split(" ")) {
            if (!word.isBlank()) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /**
     * Builds corpus-level statistics (average document length and smoothed IDF
     * per term) from the searchable texts of all chunks.
     */
    public CorpusStats buildStats(List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return new CorpusStats(0.0, Map.of());
        }

        int docCount = documents.size();
        double totalLength = 0.0;
        Map<String, Integer> documentFrequency = new HashMap<>();

        for (String document : documents) {
            Map<String, Integer> termFrequencies = new HashMap<>();
            for (String term : tokenize(document)) {
                termFrequencies.merge(term, 1, Integer::sum);
            }
            totalLength += termFrequencies.values().stream().mapToInt(Integer::intValue).sum();
            for (String term : termFrequencies.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        double avgDocLength = docCount == 0 ? 0.0 : totalLength / docCount;
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            double df = entry.getValue();
            // Smoothed BM25+ IDF, floored at 0 to avoid negative weighting.
            double value = Math.log(1.0 + (docCount - df + 0.5) / (df + 0.5));
            idf.put(entry.getKey(), Math.max(0.0, value));
        }
        return new CorpusStats(avgDocLength, idf);
    }

    /** Classic BM25 score of a single document against a query. */
    public double score(String query, String document, CorpusStats stats) {
        if (query == null || query.isBlank() || document == null || document.isBlank()
                || stats.avgDocLength() <= 0) {
            return 0.0;
        }

        Map<String, Integer> queryFrequencies = new HashMap<>();
        for (String term : tokenize(query)) {
            queryFrequencies.merge(term, 1, Integer::sum);
        }

        Map<String, Integer> documentFrequencies = new HashMap<>();
        for (String term : tokenize(document)) {
            documentFrequencies.merge(term, 1, Integer::sum);
        }
        double documentLength = documentFrequencies.values().stream().mapToInt(Integer::intValue).sum();

        double score = 0.0;
        for (Map.Entry<String, Integer> queryEntry : queryFrequencies.entrySet()) {
            Double idf = stats.idf().get(queryEntry.getKey());
            if (idf == null || idf <= 0.0) {
                continue;
            }
            int termFrequency = documentFrequencies.getOrDefault(queryEntry.getKey(), 0);
            if (termFrequency == 0) {
                continue;
            }
            double lengthRatio = documentLength / stats.avgDocLength();
            double denominator = termFrequency + K1 * (1.0 - B + B * lengthRatio);
            score += idf * (termFrequency * (K1 + 1.0)) / denominator;
        }
        return score;
    }

    /**
     * Maps a raw BM25 score into the (0, 1) range so it can be combined with
     * cosine similarities on a comparable scale: {@code raw/(1+raw)}.
     */
    public static double normalize(double rawScore) {
        return rawScore / (1.0 + rawScore);
    }
}

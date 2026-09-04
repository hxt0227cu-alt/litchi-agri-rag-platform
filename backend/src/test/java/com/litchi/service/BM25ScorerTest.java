package com.litchi.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BM25ScorerTest {

    private final BM25Scorer scorer = new BM25Scorer();

    @Test
    void tokenizesIntoBigramsAndWholeWords() {
        List<String> tokens = scorer.tokenize("深圳荔枝生产技术规程");
        assertTrue(tokens.contains("深圳"));
        assertTrue(tokens.contains("荔枝"));
        assertTrue(tokens.contains("生产"));
        assertTrue(tokens.contains("技术"));
        assertTrue(tokens.contains("规程"));
    }

    @Test
    void ranksExactTermDocumentAboveUnrelatedOne() {
        List<String> docs = List.of(
                "05 深圳地方标准 荔枝生产技术规程解读 深圳地方标准 荔枝生产技术规程解读 深圳荔枝生产技术规程解读正文",
                "09 荔枝花穗期荔枝蝽防控要点 荔枝花穗期荔枝蝽防控要点 荔枝花穗期荔枝蝽防控要点正文"
        );
        BM25Scorer.CorpusStats stats = scorer.buildStats(docs);

        double match = scorer.score("深圳荔枝生产技术规程对花果期有什么要求", docs.get(0), stats);
        double other = scorer.score("深圳荔枝生产技术规程对花果期有什么要求", docs.get(1), stats);

        assertTrue(match > other, "exact-term document must rank higher");
        assertTrue(match > 0, "matched document must have a positive score");
    }

    @Test
    void ignoresTermAbsentFromCorpus() {
        List<String> docs = List.of("荔枝炭疽病防治手册 正文");
        BM25Scorer.CorpusStats stats = scorer.buildStats(docs);
        assertEquals(0.0, scorer.score("股票基金怎么选", docs.get(0), stats), 1e-9);
    }

    @Test
    void normalizeMapsRawScoreIntoUnitRange() {
        assertEquals(0.0, BM25Scorer.normalize(0), 1e-9);
        assertEquals(0.5, BM25Scorer.normalize(1), 1e-9);
        assertTrue(BM25Scorer.normalize(50) < 1.0);
        assertTrue(BM25Scorer.normalize(50) > BM25Scorer.normalize(5));
    }

    @Test
    void handlesEmptyCorpusAndBlankQuery() {
        BM25Scorer.CorpusStats empty = scorer.buildStats(List.of());
        assertEquals(0.0, scorer.score("任意查询", "任意文档", empty), 1e-9);
        assertEquals(0.0, scorer.score("", "荔枝文档", scorer.buildStats(List.of("荔枝文档"))), 1e-9);
    }
}

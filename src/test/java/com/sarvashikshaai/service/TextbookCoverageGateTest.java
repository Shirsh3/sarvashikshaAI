package com.sarvashikshaai.service;

import com.sarvashikshaai.service.EmbeddingSearchService.RankedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextbookCoverageGateTest {

    @Test
    void refusesQuitIndiaWhenChunksAreRenaissanceOnly() {
        List<RankedChunk> hits = List.of(
                new RankedChunk(1L, 0, "The Renaissance began in Italy. Printing press spread ideas.", 0.55),
                new RankedChunk(1L, 1, "Industrial Revolution and imperialism in India for raw materials.", 0.48)
        );
        assertFalse(TextbookCoverageGate.isCovered("What is Quit India Movement", hits, 0.42));
    }

    @Test
    void refusesEvenIfIndiaAppearsAndScoreIsHigh() {
        List<RankedChunk> hits = List.of(
                new RankedChunk(1L, 0, "European powers controlled parts of India and Asia.", 0.71)
        );
        assertFalse(TextbookCoverageGate.isCovered("What is Quit India Movement", hits, 0.42));
    }

    @Test
    void allowsRenaissanceWhenChunksMatch() {
        List<RankedChunk> hits = List.of(
                new RankedChunk(1L, 0, "The Renaissance was a rebirth of art and learning in Europe.", 0.72)
        );
        assertTrue(TextbookCoverageGate.isCovered("Explain the Renaissance", hits, 0.42));
    }

    @Test
    void shortFollowUpUsesScoreOnly() {
        List<RankedChunk> hits = List.of(
                new RankedChunk(1L, 0, "Martin Luther opposed sale of indulgences.", 0.61)
        );
        assertTrue(TextbookCoverageGate.isCovered("Why?", hits, 0.42));
    }
}

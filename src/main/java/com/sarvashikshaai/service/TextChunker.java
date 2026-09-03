package com.sarvashikshaai.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple overlapping character chunks for curriculum PDF text.
 * Rough target ~1500 chars (~300–400 tokens) with 200-char overlap.
 */
@Component
public class TextChunker {

    private static final int TARGET_CHARS = 1500;
    private static final int OVERLAP_CHARS = 200;
    private static final int MAX_CHUNKS = 200;

    public List<String> chunk(String text) {
        if (text == null) return List.of();
        String cleaned = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (cleaned.isBlank()) return List.of();

        List<String> out = new ArrayList<>();
        int n = cleaned.length();
        int i = 0;
        while (i < n && out.size() < MAX_CHUNKS) {
            int end = Math.min(n, i + TARGET_CHARS);
            if (end < n) {
                int breakAt = cleaned.lastIndexOf('\n', end);
                if (breakAt > i + TARGET_CHARS / 2) {
                    end = breakAt;
                } else {
                    int space = cleaned.lastIndexOf(' ', end);
                    if (space > i + TARGET_CHARS / 2) end = space;
                }
            }
            String piece = cleaned.substring(i, end).trim();
            if (!piece.isBlank()) out.add(piece);
            if (end >= n) break;
            i = Math.max(i + 1, end - OVERLAP_CHARS);
        }
        return out;
    }

    public int estimateTokens(String chunk) {
        if (chunk == null || chunk.isBlank()) return 0;
        return Math.max(1, chunk.length() / 4);
    }
}

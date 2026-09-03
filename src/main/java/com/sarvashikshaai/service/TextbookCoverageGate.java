package com.sarvashikshaai.service;

import com.sarvashikshaai.service.EmbeddingSearchService.RankedChunk;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decides whether a Learning question is grounded in retrieved textbook chunks.
 * Used to apply the same educational-only refusal UX for out-of-chapter asks.
 */
public final class TextbookCoverageGate {

    private static final Pattern CAPITALIZED = Pattern.compile("\\b([A-Z][a-zA-Z]{2,})\\b");

    private static final Set<String> STOP = Set.of(
            "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
            "the", "and", "for", "from", "with", "about", "into", "this", "that",
            "these", "those", "them", "they", "their", "there", "here", "have", "has",
            "had", "were", "was", "are", "is", "am", "been", "being", "did", "does",
            "do", "can", "could", "would", "should", "will", "shall", "may", "might",
            "please", "give", "tell", "explain", "describe", "simple", "hindi", "english",
            "grade", "class", "student", "students", "teacher", "chapter", "question",
            "answer", "some", "many", "much", "very", "just", "also", "only", "like",
            "main", "idea", "role", "play", "after", "before", "next", "now", "then"
    );

    /** Too common in history chapters to prove a specific topic is present. */
    private static final Set<String> WEAK_ANCHORS = Set.of(
            "india", "china", "europe", "africa", "asia", "world", "america", "british",
            "britain", "french", "dutch", "german", "russia", "russian", "empire", "people",
            "country", "countries", "century", "history", "power", "powers", "trade", "war",
            "movement", "first", "second", "third"
    );

    private TextbookCoverageGate() {
    }

    /**
     * @param rawQuestion teacher question (not the expanded retrieval query)
     * @param hits        ranked chunks for the retrieval query
     * @param minScore    minimum cosine of the top hit
     */
    public static boolean isCovered(String rawQuestion, List<RankedChunk> hits, double minScore) {
        if (hits == null || hits.isEmpty()) {
            return false;
        }
        if (hits.get(0).score() < minScore) {
            return false;
        }
        Set<String> tokens = distinctiveTokens(rawQuestion);
        if (tokens.isEmpty()) {
            // Short follow-ups ("Why?", "Who?") rely on conversation-expanded retrieval + score.
            return true;
        }
        String corpus = hits.stream()
                .limit(Math.min(5, hits.size()))
                .map(RankedChunk::content)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);

        Set<String> properAnchors = properAnchors(rawQuestion);
        if (!properAnchors.isEmpty()) {
            // Every distinctive proper noun from the question must appear in retrieved text.
            for (String anchor : properAnchors) {
                if (!corpus.contains(anchor)) {
                    return false;
                }
            }
            return true;
        }

        long matches = tokens.stream().filter(corpus::contains).count();
        int need = Math.max(1, (tokens.size() + 1) / 2);
        return matches >= need;
    }

    static Set<String> distinctiveTokens(String question) {
        Set<String> out = new LinkedHashSet<>();
        if (question == null || question.isBlank()) {
            return out;
        }
        Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 4)
                .filter(t -> !STOP.contains(t))
                .forEach(out::add);
        return out;
    }

    /** Capitalized words that are not weak geo/generic history fillers. */
    static Set<String> properAnchors(String question) {
        Set<String> out = new LinkedHashSet<>();
        if (question == null || question.isBlank()) {
            return out;
        }
        Matcher m = CAPITALIZED.matcher(question);
        while (m.find()) {
            String t = m.group(1).toLowerCase(Locale.ROOT);
            if (t.length() >= 4 && !STOP.contains(t) && !WEAK_ANCHORS.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }
}

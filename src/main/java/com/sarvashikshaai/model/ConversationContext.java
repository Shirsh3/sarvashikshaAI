package com.sarvashikshaai.model;

import java.util.List;

/**
 * Conversation window for Learning RAG prompts — separate from textbook retrieval.
 */
public record ConversationContext(
        Long conversationId,
        String summary,
        List<Turn> recentTurns,
        String language,
        String explanationLevel,
        String grade,
        String subject,
        String chapterTitle
) {
    public record Turn(String role, String content) {}

    public boolean hasHistory() {
        return (summary != null && !summary.isBlank())
                || (recentTurns != null && !recentTurns.isEmpty());
    }
}

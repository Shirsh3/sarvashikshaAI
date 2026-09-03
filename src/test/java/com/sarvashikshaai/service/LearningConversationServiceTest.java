package com.sarvashikshaai.service;

import com.sarvashikshaai.model.ConversationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningConversationServiceTest {

    @Test
    void retrievalQueryIncludesTopicAndQuestionForFollowUps() {
        LearningConversationService svc = new LearningConversationService(null, null, null);
        ConversationContext ctx = new ConversationContext(
                1L,
                "Discussed sale of indulgences and Church fundraising.",
                List.of(new ConversationContext.Turn("teacher", "Explain sale of indulgences")),
                "hi",
                "simple",
                "8",
                "HISTORY",
                "Sale of Indulgences"
        );
        String q = svc.retrievalQuery("Why did the Church do this?", ctx);
        assertTrue(q.toLowerCase().contains("indulgence") || q.toLowerCase().contains("church"));
        assertTrue(q.contains("Why did the Church do this?"));
        assertFalse(q.isBlank());
    }

    @Test
    void retrievalQueryWithoutHistoryIsJustQuestion() {
        LearningConversationService svc = new LearningConversationService(null, null, null);
        String q = svc.retrievalQuery("Explain photosynthesis", null);
        assertTrue(q.equals("Explain photosynthesis"));
    }
}

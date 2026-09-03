package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.ConversationContext;
import com.sarvashikshaai.model.TeachingRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderConversationTest {

    @Test
    void promptIncludesConversationSummaryAndRecentTurns() {
        PromptBuilder pb = new PromptBuilder();
        TeachingRequest req = new TeachingRequest();
        req.setTopic("Why?");
        req.setPrepareGrade("8");
        req.setPrepareSubject("HISTORY");
        req.setChapterTitle("Sale of Indulgences");
        req.setLanguage("hi");
        req.setLearningMode(true);

        ConversationContext ctx = new ConversationContext(
                9L,
                "Teacher asked about sale of indulgences. Assistant explained Church fundraising.",
                List.of(
                        new ConversationContext.Turn("teacher", "Explain sale of indulgences in simple Hindi."),
                        new ConversationContext.Turn("assistant", "Indulgences were letters sold by the Church.")
                ),
                "hi",
                "simple",
                "8",
                "HISTORY",
                "Sale of Indulgences"
        );

        String prompt = pb.buildUnifiedTeachingPrompt(req, "[Source 1]\nChurch sold indulgences.", ctx);
        assertTrue(prompt.contains("CONVERSATION SUMMARY"));
        assertTrue(prompt.contains("RECENT CONVERSATION"));
        assertTrue(prompt.contains("TEACHING CONTEXT"));
        assertTrue(prompt.contains("followUps"));
        assertTrue(prompt.contains("CURRENT QUESTION: Why?"));
        assertTrue(prompt.contains("CLASS MATERIAL CONTEXT"));
        assertTrue(prompt.contains("TEXTBOOK GROUNDING RULE"));
        assertTrue(prompt.contains("According to your textbook"));
        assertTrue(prompt.contains("Mona Lisa"));
        assertTrue(prompt.contains("95 Theses"));
    }

    @Test
    void groundingRulePresentInLearningModeEvenWithoutRetrievedChunks() {
        PromptBuilder pb = new PromptBuilder();
        TeachingRequest req = new TeachingRequest();
        req.setTopic("Explain the Renaissance");
        req.setLearningMode(true);
        req.setPrepareMaterialId(1L);
        String prompt = pb.buildUnifiedTeachingPrompt(req, null, null);
        assertTrue(prompt.contains("TEXTBOOK GROUNDING RULE"));
        assertTrue(prompt.contains("additional information"));
        assertTrue(prompt.contains("CHAPTER SCOPE"));
        assertTrue(prompt.contains("Quit India Movement"));
    }
}

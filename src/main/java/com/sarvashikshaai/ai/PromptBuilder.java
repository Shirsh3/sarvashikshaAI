package com.sarvashikshaai.ai;

import com.sarvashikshaai.model.ConversationContext;
import com.sarvashikshaai.model.TeachingRequest;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    /**
     * Single LLM call: educational gate + three teaching sections + YouTube search phrase, as JSON.
     */
    public String buildUnifiedTeachingPrompt(TeachingRequest request) {
        return buildUnifiedTeachingPrompt(request, null, null);
    }

    public String buildUnifiedTeachingPrompt(TeachingRequest request, String retrievedContext) {
        return buildUnifiedTeachingPrompt(request, retrievedContext, null);
    }

    public String buildUnifiedTeachingPrompt(
            TeachingRequest request,
            String retrievedContext,
            ConversationContext conversation
    ) {
        String topic = safeOneLine(request.getTopic());
        String grade = safeOneLine(request.getPrepareGrade());
        boolean textbookSelected = request.isLearningMode()
                || request.getPrepareMaterialId() != null
                || (retrievedContext != null && !retrievedContext.isBlank());

        String contextBlock = "";
        if (retrievedContext != null && !retrievedContext.isBlank()) {
            String ctx = retrievedContext.length() > 6000
                    ? retrievedContext.substring(0, 6000) + "\n...[truncated]"
                    : retrievedContext;
            contextBlock = """

                CLASS MATERIAL CONTEXT (from the selected textbook PDF for this grade/chapter):
                ---
                %s
                ---
                """.formatted(ctx);
        }

        String textbookGrounding = textbookSelected ? """

                TEXTBOOK GROUNDING RULE
                When a textbook/chapter is selected, the textbook is the primary source of factual content.

                Distinguish clearly between:
                1) FACTS SUPPORTED BY THE TEXTBOOK (only claims present in CLASS MATERIAL CONTEXT)
                2) SIMPLE ANALOGIES CREATED FOR EXPLANATION (classroom metaphors only — no new historical facts)

                Do NOT introduce external historical facts merely because they are generally known.
                Examples of what NOT to invent if absent from CLASS MATERIAL CONTEXT:
                - Mona Lisa (even if Leonardo da Vinci is mentioned)
                - Galileo (when explaining the Renaissance)
                - The 95 Theses (even if Martin Luther / Reformation is mentioned)
                - Extra dates, battles, treaties, or names not in the retrieved textbook text

                If additional external knowledge would genuinely help answer the question, explicitly mark it as
                additional information NOT from the selected textbook (e.g. "Additional note (not in your textbook): …").
                Never silently present outside knowledge as textbook content.

                When the teacher asks for an analogy, you may invent a child-friendly analogy, but do not smuggle
                new historical facts through that analogy.

                Prefer phrasing like "According to your textbook…" for claims grounded in the selected material.

                CHAPTER SCOPE (same educational-only gate):
                If the CURRENT QUESTION asks about a topic, event, person, war, movement, or treaty that is NOT
                supported by CLASS MATERIAL CONTEXT (e.g. Quit India Movement when the chapter is Renaissance /
                Industrial Revolution / Opium War era only):
                → set educational=false;
                → refusal = one short polite sentence that this is not in the selected textbook chapter and they
                  should ask about something from this chapter;
                → youtubeSearchQuery=null; explanation, example, keyPoint = ""; followUps=[].
                Do NOT teach that outside topic from general knowledge.
                Do NOT fill explanation with "Additional note (not in your textbook)" for a wholly out-of-chapter topic.
                """ : "";

        String teachingPrefs = buildTeachingPrefs(request, conversation);
        String conversationBlock = buildConversationBlock(conversation);
        String gradeHint = grade.isBlank()
                ? ""
                : "\nTarget student grade: " + grade + ". Match vocabulary and examples to that grade.\n";

        String continuityRules = conversation != null && conversation.hasHistory()
                ? """

                CONVERSATION CONTINUITY:
                - Resolve pronouns and short follow-ups (why?, who?, this, he/she, next) using CONVERSATION SUMMARY and RECENT CONVERSATION.
                - Build on concepts already explained; do not restart from scratch unless asked.
                - Stay within the selected class/subject/chapter; do not mix in unrelated chapters.
                - Conversation memory resolves references; factual claims still follow TEXTBOOK GROUNDING RULE.
                """
                : "";

        return """
                You are a teaching assistant for Self-Shiksha school students (grades 1–12; simple, child-friendly language).

                """ + EducationalRedirectionPolicy.PROMPT_BLOCK + """

                Reply with ONLY valid JSON. No markdown fences, no code blocks, no text before or after the JSON object.
                Use this exact structure (all keys required):
                {
                  "educational": true or false,
                  "refusal": "string",
                  "youtubeSearchQuery": "string or null",
                  "explanation": "string",
                  "example": "string",
                  "keyPoint": "string",
                  "followUps": ["string", "string", "string"]
                }

                Rules:
                - Respond ONLY in English or Hindi (unless TEACHING CONTEXT forces one language).
                - Language selection rule:
                  - If TEACHING CONTEXT language is "hi", write refusal/explanation/example/keyPoint/followUps in Hindi.
                  - If TEACHING CONTEXT language is "en", write those fields in English.
                  - Else if the QUESTION contains any Devanagari characters (Unicode range for Hindi script), write in Hindi.
                  - Otherwise, write in English.

                - followUps: 3–4 short teacher follow-up questions (chips) grounded in this answer and topic. Not generic.
                - Classify the QUESTION:
                  (A) Hard-block: sexual content, hate/harassment, self-harm how-to, extreme graphic violence, illegal how-to, malware, exam cheating, harvesting personal data, or requests to rate/score/compare real people by beauty/attractiveness/looks/figure/body (e.g., "Rate Indian actress", "Who is hottest", "compare actresses", "Explain Aishwarya Rai figure") → set educational=false; refusal = one short polite sentence that only educational classroom content is allowed; youtubeSearchQuery=null; explanation, example, keyPoint = ""; followUps=[].

                  (B) Allowed but superficial / non-educational (gossip, "best hairstyle" style questions, idle celebrity chatter WITHOUT looks/figure rating) → set educational=TRUE, refusal="". Do NOT rate people or compare looks. Do NOT shame the asker. Pivot using the TRANSFORM steps above across the fields:
                    • explanation: acknowledge + generalise (2–4 short sentences).
                    • example: one concrete educational angle (storytelling, culture, history, science, or media literacy).
                    • keyPoint: one direct takeaway sentence that answers the learner’s doubt (do NOT ask a follow-up question).
                    • youtubeSearchQuery: short neutral ENGLISH phrase for the pivoted educational topic (for YouTube API search).
                    • followUps: 2–3 short educational follow-ups.

                  (C) Normal school-style question covered by the selected chapter / CLASS MATERIAL CONTEXT → educational=true, refusal="", fill explanation as a detailed teaching block (5–7 short sentences, step-by-step, include what/why/how where relevant), example (one concrete relatable example), keyPoint (one memorable takeaway sentence, not a question). youtubeSearchQuery = short neutral ENGLISH phrase for the topic. followUps = 3–4 useful next questions.

                  (D) When a textbook/chapter is selected and the question is educational but NOT in CLASS MATERIAL CONTEXT → same as CHAPTER SCOPE above: educational=false + short refusal (do not teach from outside knowledge).

                - For educational=true responses, prioritize explanation depth:
                  - explanation should be the longest field.
                  - Use plain school-level wording but include enough detail for classroom teaching.
                  - Avoid one-line explanations unless the question itself is extremely simple.
                - keyPoint must be a statement/answer, never phrased as a question.

                - Never include harmful or inappropriate content.
                """ + teachingPrefs + gradeHint + continuityRules + textbookGrounding + conversationBlock + contextBlock + """

                CURRENT QUESTION: %s
                """.formatted(topic);
    }

    private static String buildTeachingPrefs(TeachingRequest request, ConversationContext conversation) {
        String lang = firstNonBlank(
                request.getLanguage(),
                conversation != null ? conversation.language() : null,
                "auto");
        String level = firstNonBlank(
                request.getExplanationLevel(),
                conversation != null ? conversation.explanationLevel() : null,
                "simple");
        String subject = firstNonBlank(
                request.getPrepareSubject(),
                conversation != null ? conversation.subject() : null,
                "");
        String chapter = firstNonBlank(
                request.getChapterTitle(),
                conversation != null ? conversation.chapterTitle() : null,
                "");
        String grade = firstNonBlank(
                request.getPrepareGrade(),
                conversation != null ? conversation.grade() : null,
                "");
        return """

                TEACHING CONTEXT:
                Class/Grade: %s
                Subject: %s
                Chapter/Topic: %s
                Language: %s
                Explanation level: %s
                Style: Simple and easy to understand for classroom teaching.
                """.formatted(
                blankDash(grade),
                blankDash(subject),
                blankDash(chapter),
                lang,
                level
        );
    }

    private static String buildConversationBlock(ConversationContext conversation) {
        if (conversation == null || !conversation.hasHistory()) return "";
        StringBuilder sb = new StringBuilder();
        if (conversation.summary() != null && !conversation.summary().isBlank()) {
            String sum = conversation.summary().length() > 2000
                    ? conversation.summary().substring(0, 2000) + "…"
                    : conversation.summary();
            sb.append("\nCONVERSATION SUMMARY:\n").append(sum).append("\n");
        }
        if (conversation.recentTurns() != null && !conversation.recentTurns().isEmpty()) {
            sb.append("\nRECENT CONVERSATION:\n");
            for (ConversationContext.Turn t : conversation.recentTurns()) {
                String role = "teacher".equalsIgnoreCase(t.role()) ? "Teacher" : "Assistant";
                sb.append(role).append(": ").append(safeOneLine(t.content())).append('\n');
            }
        }
        return sb.toString();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static String blankDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s.trim();
    }

    private static String safeOneLine(String t) {
        if (t == null) return "";
        return t.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').strip();
    }
}

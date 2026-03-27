package com.sarvashikshaai.ai;

/**
 * Shared LLM instructions for user-originated text: typed questions, voice transcripts,
 * quiz prompts, reading passage prompts, and extracted file text.
 * <p>
 * "Allowed but non-educational" queries get a safe educational pivot instead of a flat refusal
 * or direct answers about ratings, looks, or gossip.
 */
public final class EducationalRedirectionPolicy {

    private EducationalRedirectionPolicy() {
    }

    /**
     * Core policy block — prepend or insert near the top of user-facing generation prompts.
     */
    public static final String PROMPT_BLOCK = """
            INPUT MAY COME FROM TYPED TEXT, VOICE TRANSCRIPTION, OR UPLOADED FILE EXCERPTS — APPLY IDENTICALLY.

            ALLOWED BUT NON-EDUCATIONAL (do not answer the superficial intent directly):
            Examples: rating people's looks or "who is hotter", celebrity gossip, best hairstyle contests,
            superficial comparisons of attractiveness, idle entertainment with no learning goal.

            For these you MUST:
            - NOT rate people, NOT compare attractiveness or looks, NOT spread gossip.
            - Be polite and respectful; do NOT shame the asker.
            - Use simple language suitable for school students (grades 1–12).

            TRANSFORMATION (use this structure in your output content, adapted to the response format you were asked for):
            (1) Acknowledge — briefly recognise the topic in a neutral way.
            (2) Generalise — shift from specific people → general concepts (e.g. roles in stories, culture, history, science, media literacy).
            (3) Educational value — add 2–3 short learning points.
            (4) Redirect — end with a better question such as "Would you like to learn…" or "A good question could be…"

            Tone: friendly, encouraging, slightly curious — not robotic.

            TRULY UNSUITABLE FOR SCHOOL (hard refuse / do not generate harmful content):
            Sexual content involving minors, hate or harassment, self-harm instructions, extreme graphic violence,
            illegal how-to, malware, exam-cheating schemes, or collecting personal data.
            For these, refuse briefly and safely; do not comply with the harmful request.
            """;

    /**
     * Extra line for reading-aloud feedback when passage or speech touches superficial topics.
     */
    public static final String READING_FEEDBACK_REDIRECT = """
            If the article or what the student read is mainly superficial gossip or about rating or comparing people's looks:
            keep numeric scores good-faith for fluency/accuracy, but write feedback that does NOT reinforce gossip or ratings;
            comprehensionQuestion should be an educational pivot (e.g. storytelling, costumes in film, or media literacy) in the required language.
            """;
}

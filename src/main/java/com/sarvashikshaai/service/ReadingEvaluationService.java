package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.EducationalRedirectionPolicy;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.ai.StrictEducationalGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Evaluates student reading (fluency, pronunciation, etc.) via OpenAI.
 * Used by the Reading practice flow only; no news or RSS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingEvaluationService {

    public record ReadingAttempt(
        String text,
        double durationSeconds,
        int wordCount,
        int attemptNumber
    ) {}

    public record ReadingRequest(
        String studentName,
        String articleTitle,
        String originalText,
        String spokenText,
        List<ReadingAttempt> readingAttempts,
        Double measuredWpm,
        String rehearsedWordsClient,
        String practiceSessionSummary
    ) {
        public ReadingRequest {
            if (readingAttempts == null) {
                readingAttempts = List.of();
            }
            if (rehearsedWordsClient == null) {
                rehearsedWordsClient = "";
            }
            if (practiceSessionSummary == null) {
                practiceSessionSummary = "";
            }
        }
    }

    public record ReadingFeedback(
        int    fluencyScore,
        int    pronunciationScore,
        int    paceScore,
        int    accuracyScore,
        int    confidenceScore,
        int    originalWordCount,
        int    spokenWordCount,
        int    accuracyPercent,
        String hindiFeedback,
        String englishFeedback,
        String comprehensionQuestion,
        String difficultWords,
        String goodWords,
        String improvementTip,
        String punctuationCoach,
        String paceCoach,
        String repeatedWordsCoach
    ) {}

    /** AI-generated or normalized passage from what the teacher typed (prompt or full text). */
    public record GeneratedReadingPassage(String title, String passage) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAIClient openAIClient;

    /** Injected into LLM prompts so generated/normalised reading text stays classroom-safe. */
    private static final String EDUCATIONAL_ONLY_READING = """
            STRICT — EDUCATIONAL / CLASSROOM USE ONLY:
            - Output must be suitable for school students (about ages 8–16): learning, general knowledge, literature-style informative text, values, science, history, language practice, or clearly fictional but wholesome stories.
            - No sexual content, hate or harassment, graphic violence, glorification of self-harm, instructions for wrongdoing, drugs, scams, gambling, or polarising political campaigning.
            - If the teacher input asks for anything non-educational or unsafe, do NOT comply with that request: instead produce JSON with a short, positive educational passage (e.g. benefits of reading and curiosity) and title "Practice passage".
            """;

    private static final String EDUCATIONAL_ONLY_FEEDBACK = """
            All feedback fields must stay strictly educational and classroom-appropriate. Do not repeat or elaborate harmful, sexual, hateful, or illegal content from the article; give only neutral reading-coaching feedback.
            """;

    /**
     * Turns teacher box input into a real reading passage when they ask to generate one;
     * if they already pasted a full passage, returns it cleaned lightly.
     */
    public GeneratedReadingPassage generateReadingPassage(String teacherInput) {
        return generateReadingPassage(teacherInput, null, null);
    }

    public GeneratedReadingPassage generateReadingPassage(String teacherInput, String grade) {
        return generateReadingPassage(teacherInput, grade, null);
    }

    /**
     * @param readingLanguageCategory optional teacher choice: Hindi, English, or Hinglish (from UI dropdown); null = auto
     */
    public GeneratedReadingPassage generateReadingPassage(String teacherInput, String grade, String readingLanguageCategory) {
        if (teacherInput == null || teacherInput.isBlank()) {
            return new GeneratedReadingPassage("Reading passage", "");
        }
        String trimmed = teacherInput.trim();
        if (openAIClient.classifyUserQuery(trimmed) != OpenAIClient.QueryCategory.LEARNING) {
            return new GeneratedReadingPassage(
                    "Practice passage",
                    "Only educational classroom content is allowed. Please ask a school-related topic."
            );
        }
        if (StrictEducationalGuard.isBlocked(trimmed)) {
            return new GeneratedReadingPassage(
                    "Practice passage",
                    "School learning helps us build knowledge, discipline, and confidence. "
                            + "Regular reading improves vocabulary and understanding. "
                            + "A good classroom question is about ideas, science, language, or history."
            );
        }

        String gradeLine = (grade == null || grade.isBlank())
                ? "Not specified — infer age-appropriate reading level from the teacher request."
                : grade.trim();

        String languageBlock = "";
        if (readingLanguageCategory != null && !readingLanguageCategory.isBlank()) {
            String cat = normalizeReadingLanguageCategory(readingLanguageCategory.trim());
            languageBlock = switch (cat) {
                case "Hindi" -> """

                        Teacher-selected language: Hindi.
                        Write BOTH the JSON "title" and "passage" in Hindi using Devanagari script.
                        """;
                case "Hinglish" -> """

                        Teacher-selected language: Hinglish (Hindi ideas in Latin letters, mixed with English as in Indian classrooms).
                        Write BOTH the JSON "title" and "passage" in natural Hinglish using Roman letters only (not Devanagari).
                        """;
                default -> """

                        Teacher-selected language: English.
                        Write BOTH the JSON "title" and "passage" in standard English using Latin script.
                        """;
            };
        }

        String promptHead = """
            You help Indian school teachers prepare reading practice for students aged 8-16.

            """ + EDUCATIONAL_ONLY_READING + """

            """ + EducationalRedirectionPolicy.PROMPT_BLOCK + """

            The teacher typed the following in one box. It may be:
            (1) Instructions such as "Generate a passage on Ram Dhari" or "Write 150 words about photosynthesis in Hindi"
            (2) Already a full passage copied from a textbook for students to read

            Target grade for reading level (vocabulary + sentence length MUST match this band):
            %s
            %s

            Teacher input (verbatim):

            """.formatted(gradeLine, languageBlock);
        String defaultLangRule = (readingLanguageCategory == null || readingLanguageCategory.isBlank())
                ? "Use English unless they clearly ask for Hindi (Devanagari script)."
                : "Follow the teacher-selected language above; do not switch language unless the teacher input explicitly contradicts it.";

        String promptTail = """

            Reading comprehension format (mandatory):
            - The passage is for classroom reading comprehension: it must be a complete, self-contained piece that does not rely on follow-up discussion.
            - Do NOT address the reader with questions (no "What do you think?", "Why?", "Can you guess?", "Discuss", or any direct question to the student inside the passage).
            - Do NOT end with rhetorical questions or prompts that invite an oral answer; end with clear statements or a definitive closing sentence.
            - If the input is already a pasted passage that ends with questions to the reader, rewrite minimally so the text stays informative but remove such questions (keep educational meaning).

            Your task:
            - If the input is mainly a request to create or generate reading material: write an original, factual passage (about 120-220 words unless they specify length) whose difficulty clearly matches the target grade — do NOT use the same complexity for every grade. %s
            - If the request is allowed but superficial (gossip, ratings, comparisons of looks): write a passage that follows the TRANSFORMATION strategy above inside the passage body (still JSON title + passage).
            - If the input is already a student passage: keep the meaning; fix only obvious spelling or punctuation. Do not replace the whole text with a different topic.
            - If pasted or requested content is unsafe or not suitable for school, output JSON with a short safe educational passage instead (title "Practice passage"); do not reproduce harmful text.

            Respond ONLY with valid JSON (no markdown, no code fences):
            {"title":"Short descriptive title","passage":"Full passage text; you may use newline characters inside the string for paragraphs"}
            """.formatted(defaultLangRule);
        String prompt = promptHead + trimmed + promptTail;

        try {
            String raw = openAIClient.generateCompletion(prompt);
            String json = extractFirstJsonObject(unFenceMarkdown(raw));
            JsonNode n = mapper.readTree(json);
            String title = n.path("title").asText("Reading passage");
            String passage = n.path("passage").asText("").trim();
            if (passage.isBlank()) {
                return new GeneratedReadingPassage("Reading passage", trimmed);
            }
            return new GeneratedReadingPassage(
                    title.isBlank() ? "Reading passage" : title,
                    passage);
        } catch (Exception e) {
            log.warn("Reading passage generation failed: {} — input preview: {}",
                    e.getMessage(), previewForLog(trimmed, 120));
            return new GeneratedReadingPassage("Reading passage", trimmed);
        }
    }

    private static String unFenceMarkdown(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int fence = s.indexOf("```");
        if (fence < 0) {
            return s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int nl = s.indexOf('\n', fence);
        int contentStart = nl >= 0 ? nl + 1 : fence + 3;
        int endFence = s.lastIndexOf("```");
        if (endFence > fence) {
            return s.substring(contentStart, endFence).trim();
        }
        return s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }

    /** First top-level JSON object; brace depth ignores strings so passages may contain "}". */
    private static String extractFirstJsonObject(String s) {
        if (s == null) return "";
        String t = s.trim();
        int start = t.indexOf('{');
        if (start < 0) return t;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return t.substring(start, i + 1);
                }
            }
        }
        return t.substring(start);
    }

    private static String normalizeReadingLanguageCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return "English";
        }
        String s = raw.trim();
        if (s.equalsIgnoreCase("Hindi")) {
            return "Hindi";
        }
        if (s.equalsIgnoreCase("Hinglish")) {
            return "Hinglish";
        }
        return "English";
    }

    private static String previewForLog(String text, int max) {
        if (text == null) return "";
        String oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= max) return oneLine;
        return oneLine.substring(0, max) + "…";
    }

    /** Max characters of original passage sent to the model (book excerpts can be very long). */
    private static final int MAX_ORIGINAL_TEXT_IN_PROMPT = 10_000;

    public ReadingFeedback evaluateReading(ReadingRequest req) {
        int origWords   = countWords(req.originalText());
        int spokenWords = countWords(req.spokenText());

        String attemptsBlock = formatAttemptsForPrompt(req.readingAttempts());
        String wpmLine = req.measuredWpm() != null && req.measuredWpm() > 0
            ? "Approximate speaking rate from the last recorded segment (client-estimated): about %.0f words per minute.\n".formatted(req.measuredWpm())
            : "";
        String rehearsedLine = req.rehearsedWordsClient().isBlank()
            ? ""
            : "Words the practice app flagged as rehearsed in more than one take (may include false positives): %s\n"
                .formatted(req.rehearsedWordsClient());
        String summaryLine = req.practiceSessionSummary().isBlank()
            ? ""
            : "Per-segment practice log from the app:\n%s\n".formatted(req.practiceSessionSummary());

        boolean oralOnlyNoPassage = req.originalText() == null || req.originalText().isBlank();

        String prompt;
        if (oralOnlyNoPassage) {
            prompt = buildOralOnlyEvaluationPrompt(req, attemptsBlock, wpmLine, rehearsedLine, summaryLine);
        } else {
            String fullOriginal = req.originalText();
            boolean originalTruncated = fullOriginal.length() > MAX_ORIGINAL_TEXT_IN_PROMPT;
            String originalForPrompt = originalTruncated
                ? truncate(fullOriginal, MAX_ORIGINAL_TEXT_IN_PROMPT)
                : fullOriginal;
            String truncationNote = originalTruncated
                ? "\nNote: The passage was long; only the first part is shown above. Compare the student's speech to this excerpt; word-count fields in the app use the full passage length.\n"
                : "";

            prompt = """
            You are a warm, encouraging reading coach for school students aged 8-16 in India.

            """ + EDUCATIONAL_ONLY_FEEDBACK + """

            """ + EducationalRedirectionPolicy.READING_FEEDBACK_REDIRECT + """

            Article title: "%s"
            Original article text: "%s"
            Final combined transcript of what the student read aloud (use this as the main basis for accuracy): "%s"

            %s%s%s%s%s

            The student may have recorded in several short segments (re-tries). Use the final combined transcript for matching the passage; use the segment log only to infer hesitation, pace changes, and words they practised again.

            Language rules: If the original passage OR the spoken transcript is primarily Hindi (Devanagari), then difficultWords and goodWords MUST be comma-separated words/phrases in Devanagari; punctuationCoach and paceCoach MUST be 1-2 short sentences in simple Hindi (Devanagari). If primarily English, use English for those four fields.

            Carefully compare the original text with what the student said, then respond ONLY with this JSON \
            (no markdown, no extra text outside the JSON). Do not use or request any student or personal names.
            {
              "fluencyScore":        <integer 1-10, overall smoothness and flow>,
              "pronunciationScore":  <integer 1-10, clarity of word pronunciation>,
              "paceScore":           <integer 1-10, 10=perfect pace, low=too fast or too slow>,
              "accuracyScore":       <integer 1-10, how closely the spoken text matches original words>,
              "confidenceScore":     <integer 1-10, confidence and hesitation level>,
              "accuracyPercent":     <integer 0-100, estimated percentage of original words correctly spoken>,
              "difficultWords":      "<comma-separated list of up to 5 words the student struggled with or skipped, empty string if none>",
              "goodWords":           "<comma-separated list of up to 5 words the student pronounced particularly well>",
              "hindiFeedback":       "<2-3 warm sentences in Hindi: one strength, one thing to improve, encouraging close. Do not use any personal name.>",
              "englishFeedback":     "<Same content as hindiFeedback but in English. Do not use any personal name.>",
              "comprehensionQuestion": "<One simple question in Hindi about the article content to check understanding>",
              "improvementTip":      "<One short, specific, actionable tip in English for next practice session>",
              "punctuationCoach":    "<1-2 sentences on commas, full stops, pauses — Hindi if passage Hindi, else English>",
              "paceCoach":           "<1-2 sentences on reading speed — Hindi if passage Hindi, else English>",
              "repeatedWordsCoach":  "<If rehearsed-word hints were given, 1-3 short friendly tips on pronouncing those words; otherwise a brief encouragement>"
            }
            """.formatted(
                escapePrompt(req.articleTitle()),
                escapePrompt(originalForPrompt),
                escapePrompt(req.spokenText()),
                wpmLine,
                rehearsedLine,
                summaryLine,
                truncationNote,
                attemptsBlock
            );
        }

        try {
            String raw = openAIClient.generateCompletion(prompt);
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            JsonNode n = mapper.readTree(raw);
            int accuracyPct = n.path("accuracyPercent").asInt(
                oralOnlyNoPassage ? 0 : computeSimpleAccuracy(req.originalText(), req.spokenText()));
            return new ReadingFeedback(
                n.path("fluencyScore").asInt(5),
                n.path("pronunciationScore").asInt(5),
                n.path("paceScore").asInt(5),
                n.path("accuracyScore").asInt(5),
                n.path("confidenceScore").asInt(5),
                origWords,
                spokenWords,
                accuracyPct,
                n.path("hindiFeedback").asText(""),
                n.path("englishFeedback").asText(""),
                n.path("comprehensionQuestion").asText(""),
                n.path("difficultWords").asText(""),
                n.path("goodWords").asText(""),
                n.path("improvementTip").asText(""),
                n.path("punctuationCoach").asText(""),
                n.path("paceCoach").asText(""),
                n.path("repeatedWordsCoach").asText("")
            );
        } catch (Exception e) {
            log.error("Reading evaluation failed: {}", e.getMessage());
            int acc = computeSimpleAccuracy(req.originalText(), req.spokenText());
            return new ReadingFeedback(
                5, 5, 5, 5, 5,
                origWords, spokenWords, acc,
                "पढ़ने का प्रयास अच्छा था! अगली बार और ध्यान से पढ़ो।",
                "Good attempt! Keep practising.",
                "", "", "", "Practice reading one sentence at a time.",
                "", "", ""
            );
        }
    }

    private static String escapePrompt(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String formatAttemptsForPrompt(List<ReadingAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return "";
        }
        String lines = attempts.stream()
            .map(a -> "Segment %d (~%d words, %.1f s): \"%s\""
                .formatted(
                    a.attemptNumber(),
                    a.wordCount(),
                    a.durationSeconds(),
                    escapePrompt(truncate(a.text(), 400))))
            .collect(Collectors.joining("\n"));
        return "Recorded segments (snippets, in order):\n" + lines + "\n";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    /**
     * Student reads from a physical book with no pasted reference — only the speech transcript is available.
     */
    private String buildOralOnlyEvaluationPrompt(
            ReadingRequest req,
            String attemptsBlock,
            String wpmLine,
            String rehearsedLine,
            String summaryLine) {
        String spoken = req.spokenText() == null ? "" : req.spokenText();
        String spokenForPrompt = spoken.length() > MAX_ORIGINAL_TEXT_IN_PROMPT
            ? truncate(spoken, MAX_ORIGINAL_TEXT_IN_PROMPT)
            : spoken;
        String title = req.articleTitle() != null && !req.articleTitle().isBlank()
            ? req.articleTitle().trim()
            : "Reading aloud";

        return """
            You are a warm, encouraging reading coach for school students aged 8-16 in India.

            """ + EDUCATIONAL_ONLY_FEEDBACK + """

            """ + EducationalRedirectionPolicy.READING_FEEDBACK_REDIRECT + """

            Context: The student read aloud from their own book. There is NO separate reference passage — only what the microphone captured is below.

            Session label: "%s"

            Full transcript of what was recognised from the student's speech (this is the ONLY text to judge):
            "%s"

            %s%s%s%s

            Do NOT claim they missed words compared to a hidden passage — there is none. Judge oral reading quality: fluency, clarity, pace, confidence, and how intelligible the transcript is.
            If the transcript is very short or empty, give gentle encouragement to read a bit longer next time and keep scores low but kind.

            Language rules: If the transcript is primarily Hindi (Devanagari), difficultWords and goodWords MUST be in Devanagari; punctuationCoach and paceCoach MUST be in simple Hindi. If primarily English, use English for those four fields.

            Respond ONLY with valid JSON (no markdown, no extra text). Do not use or request any student or personal names.
            {
              "fluencyScore":        <integer 1-10, smoothness and flow of reading>,
              "pronunciationScore":  <integer 1-10, clarity of words as heard in the transcript>,
              "paceScore":           <integer 1-10, 10=good pace for age, low=too fast or too slow>,
              "accuracyScore":       <integer 1-10, how clear and complete the oral reading sounds (not vs a fixed text)>,
              "confidenceScore":     <integer 1-10, confidence from pacing and hesitation>,
              "accuracyPercent":     <integer 0-100, overall intelligibility / quality of the reading as a whole>,
              "difficultWords":      "<up to 5 words that sound unclear or hesitant in the transcript, empty if none>",
              "goodWords":           "<up to 5 words read clearly, empty if none>",
              "hindiFeedback":       "<2-3 warm sentences in Hindi: strength, improvement, encouragement. No names.>",
              "englishFeedback":     "<Same as hindiFeedback in English>",
              "comprehensionQuestion": "<One simple Hindi question about a fact or idea that appears IN the transcript (if too short, ask them to read a full sentence next time)>",
              "improvementTip":      "<One actionable English tip for next time>",
              "punctuationCoach":    "<1-2 sentences on pausing at punctuation — Hindi if transcript Hindi, else English>",
              "paceCoach":           "<1-2 sentences on speed — Hindi if transcript Hindi, else English>",
              "repeatedWordsCoach":  "<Brief note on words practised again, or encouragement>"
            }
            """.formatted(
                escapePrompt(title),
                escapePrompt(spokenForPrompt),
                wpmLine,
                rehearsedLine,
                summaryLine,
                attemptsBlock
            );
    }

    private static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private static int computeSimpleAccuracy(String original, String spoken) {
        if (original == null || original.isBlank() || spoken == null || spoken.isBlank()) return 0;
        Set<String> spokenWords = Arrays.stream(
                        spoken.toLowerCase().replaceAll("[^a-z\\s]", " ").trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .collect(Collectors.toSet());
        String[] origWords = original.toLowerCase().replaceAll("[^a-z\\s]", " ").trim().split("\\s+");
        if (origWords.length == 0) return 0;
        long matched = Arrays.stream(origWords)
                .filter(w -> !w.isBlank())
                .filter(spokenWords::contains)
                .count();
        return (int) Math.round(100.0 * matched / origWords.length);
    }
}

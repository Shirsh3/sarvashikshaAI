package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.EducationalRedirectionPolicy;
import com.sarvashikshaai.ai.OpenAIClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
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
        if (teacherInput == null || teacherInput.isBlank()) {
            return new GeneratedReadingPassage("Reading passage", "");
        }
        String trimmed = teacherInput.trim();

        String prompt = """
            You help Indian school teachers prepare reading practice for students aged 8-16.

            """ + EDUCATIONAL_ONLY_READING + """

            """ + EducationalRedirectionPolicy.PROMPT_BLOCK + """

            The teacher typed the following in one box. It may be:
            (1) Instructions such as "Generate a passage on Ram Dhari" or "Write 150 words about photosynthesis in Hindi"
            (2) Already a full passage copied from a textbook for students to read

            Teacher input (verbatim):

            """ + trimmed + """

            Your task:
            - If the input is mainly a request to create or generate reading material: write an original, factual, age-appropriate passage (about 120-220 words unless they specify length). Use English unless they clearly ask for Hindi (Devanagari script).
            - If the request is allowed but superficial (gossip, ratings, comparisons of looks): write a passage that follows the TRANSFORMATION strategy above inside the passage body (still JSON title + passage).
            - If the input is already a student passage: keep the meaning; fix only obvious spelling or punctuation. Do not replace the whole text with a different topic.
            - If pasted or requested content is unsafe or not suitable for school, output JSON with a short safe educational passage instead (title "Practice passage"); do not reproduce harmful text.

            Respond ONLY with valid JSON (no markdown, no code fences):
            {"title":"Short descriptive title","passage":"Full passage text; you may use newline characters inside the string for paragraphs"}
            """;

        try {
            String raw = openAIClient.generateCompletion(prompt);
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            JsonNode n = mapper.readTree(raw);
            String title = n.path("title").asText("Reading passage");
            String passage = n.path("passage").asText("").trim();
            if (passage.isBlank()) {
                return new GeneratedReadingPassage("Reading passage", trimmed);
            }
            return new GeneratedReadingPassage(
                    title.isBlank() ? "Reading passage" : title,
                    passage);
        } catch (Exception e) {
            log.error("Reading passage generation failed: {}", e.getMessage());
            return new GeneratedReadingPassage("Reading passage", trimmed);
        }
    }

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

        String prompt = """
            You are a warm, encouraging reading coach for school students aged 8-16 in India.

            """ + EDUCATIONAL_ONLY_FEEDBACK + """

            """ + EducationalRedirectionPolicy.READING_FEEDBACK_REDIRECT + """

            Article title: "%s"
            Original article text: "%s"
            Final combined transcript of what the student read aloud (use this as the main basis for accuracy): "%s"

            %s%s%s%s

            The student may have recorded in several short segments (re-tries). Use the final combined transcript for matching the passage; use the segment log only to infer hesitation, pace changes, and words they practised again.

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
              "punctuationCoach":    "<1-2 sentences in simple English on commas, full stops, and pauses — relate to how they read THIS passage>",
              "paceCoach":           "<1-2 sentences in simple English on reading speed — reference the client's WPM hint or segment log if useful>",
              "repeatedWordsCoach":  "<If rehearsed-word hints were given, 1-3 short friendly tips on pronouncing those words; otherwise a brief encouragement>"
            }
            """.formatted(
                escapePrompt(req.articleTitle()),
                escapePrompt(req.originalText()),
                escapePrompt(req.spokenText()),
                wpmLine,
                rehearsedLine,
                summaryLine,
                attemptsBlock
            );

        try {
            String raw = openAIClient.generateCompletion(prompt);
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            JsonNode n = mapper.readTree(raw);
            int accuracyPct = n.path("accuracyPercent").asInt(computeSimpleAccuracy(req.originalText(), req.spokenText()));
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

    private static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private static int computeSimpleAccuracy(String original, String spoken) {
        if (original == null || original.isBlank() || spoken == null || spoken.isBlank()) return 0;
        Set<String> spokenWords = Set.of(spoken.toLowerCase().replaceAll("[^a-z\\s]", "").trim().split("\\s+"));
        String[] origWords = original.toLowerCase().replaceAll("[^a-z\\s]", "").trim().split("\\s+");
        if (origWords.length == 0) return 0;
        long matched = java.util.Arrays.stream(origWords).filter(spokenWords::contains).count();
        return (int) Math.round(100.0 * matched / origWords.length);
    }
}

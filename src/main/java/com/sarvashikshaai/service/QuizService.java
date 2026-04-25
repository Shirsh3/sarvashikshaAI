package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.EducationalRedirectionPolicy;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.ai.StrictEducationalGuard;
import com.sarvashikshaai.model.entity.QuizEntity;
import com.sarvashikshaai.model.entity.QuizQuestionEntity;
import com.sarvashikshaai.model.entity.QuestionResponseEntity;
import com.sarvashikshaai.model.entity.StudentEntity;
import com.sarvashikshaai.repository.QuestionResponseRepository;
import com.sarvashikshaai.repository.QuizQuestionRepository;
import com.sarvashikshaai.repository.QuizRepository;
import com.sarvashikshaai.repository.StudentEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    @Value("${openai.quiz.cover-image.enabled:false}")
    private boolean quizCoverImageEnabled;

    private final QuizRepository       quizRepo;
    private final QuizQuestionRepository questionRepo;
    private final QuestionResponseRepository questionResponseRepo;
    private final StudentEntityRepository studentRepo;
    private final OpenAIClient         openAIClient;
    private final ObjectMapper         mapper = new ObjectMapper();

    /** Injected into all quiz-generation prompts. */
    private static final String EDUCATIONAL_ONLY_QUIZ = """
            STRICT — EDUCATIONAL / CLASSROOM USE ONLY:
            - Every question, option, and answer must be suitable for school (about ages 8–16): curriculum-style learning, general knowledge, or clearly benign puzzles tied to the topic.
            - No sexual content, hate or harassment, graphic violence, self-harm, instructions for wrongdoing, drugs, scams, gambling, or polarising political campaigning.
            - If the teacher topic or file content asks for anything non-educational or unsafe, refuse that angle: generate only safe, neutral educational questions on a closely related school-appropriate topic instead.
            """;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<QuizEntity> listAll()                     { return quizRepo.findAllByOrderByCreatedAtDesc(); }
    public Optional<QuizEntity> findById(Long id)         { return quizRepo.findById(id); }
    @Transactional
    public void delete(Long id) {
        questionResponseRepo.deleteByQuestion_QuizId(id);
        questionRepo.deleteByQuizId(id);
        quizRepo.deleteById(id);
    }

    public QuizEntity save(String topic, String grade, String description, String questionsJson) {
        return save(topic, grade, description, questionsJson, false);
    }

    @Transactional
    public QuizEntity save(String topic, String grade, String description, String questionsJson, boolean handoutOnly) {
        List<QuestionData> questions = parseQuestions(questionsJson);
        int count = questions.size();
        String cleanTopic = topic != null ? topic.trim() : "";
        if (cleanTopic.isBlank()) cleanTopic = "General";
        String cleanGrade = grade != null ? grade.trim() : "";
        String cleanDescription = description != null ? description.trim() : "";
        String title = cleanGrade.isBlank()
                ? "Quiz: " + cleanTopic
                : "Quiz (Grade " + cleanGrade + "): " + cleanTopic;
        QuizEntity quiz = new QuizEntity(
                title,
                cleanTopic,        // subject (legacy)
                cleanTopic,        // topic
                cleanGrade,
                cleanDescription,
                count
        );
        quiz.setHandoutOnly(handoutOnly);
        QuizEntity saved = quizRepo.save(quiz);
        if (quizCoverImageEnabled) {
            openAIClient.generateQuizCoverImageUrl(cleanTopic, cleanGrade).ifPresent(url -> {
                saved.setCoverImageUrl(url);
                quizRepo.save(saved);
            });
        }
        String quizCover = saved.getCoverImageUrl();
        saveQuestions(saved.getId(), questions, quizCover);
        return saved;
    }

    public List<QuestionData> getQuestionsByQuiz(Long quizId) {
        String quizCover = quizRepo.findById(quizId)
                .map(QuizEntity::getCoverImageUrl)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
        return questionRepo.findByQuizIdOrderByQuestionOrderAsc(quizId).stream()
                .map(q -> new QuestionData(
                        q.getId(),
                        q.getQuestionType(),
                        q.getQuestionText(),
                        buildOptions(q),
                        q.getCorrectAnswer(),
                        q.getAnswerExplanation(),
                        resolveWatermarkForDisplay(q, quizCover)
                ))
                .toList();
    }

    /** Stable Picsum image per quiz+order; used when DB has no URL yet. */
    private static String buildPicsumWatermarkUrl(long quizId, int questionOrder) {
        String seed = "qz" + quizId + "n" + questionOrder;
        return "https://picsum.photos/seed/" + seed + "/1600/900";
    }

    /** Quiz-level OpenAI cover wins for every question when set. */
    private static String resolveWatermarkForDisplay(QuizQuestionEntity q, String quizCoverUrl) {
        if (quizCoverUrl != null && !quizCoverUrl.isBlank()) {
            return quizCoverUrl.trim();
        }
        String u = q.getWatermarkImageUrl();
        if (u != null && !u.isBlank()) {
            return u.trim();
        }
        return buildPicsumWatermarkUrl(q.getQuizId(), q.getQuestionOrder());
    }

    // ── AI question generation ────────────────────────────────────────────────

    private String buildTypeInstruction(String questionTypes, int count) {
        if (questionTypes == null) {
            questionTypes = "";
        }
        String one = questionTypes.trim();
        if ("SHORT".equalsIgnoreCase(one)) {
            return "ALL " + count + " questions MUST be type SHORT (Short Answer). Do NOT generate any MCQ or True/False. Every item in the JSON array must have \"type\":\"SHORT\" with \"text\" and \"answer\" only (no options).";
        }
        if ("MCQ".equalsIgnoreCase(one)) {
            return "ALL " + count + " questions MUST be type MCQ (Multiple Choice). "
                    + "EVERY object in the JSON array MUST have exactly \\\"type\\\":\\\"MCQ\\\". "
                    + "It is an error to output any item with type SHORT, LONG, TF, or any value other than MCQ. "
                    + "Each item must have exactly 4 string options and answer equal to one option verbatim.";
        }
        if ("TF".equalsIgnoreCase(one)) {
            return "ALL " + count + " questions MUST be type TF (True/False). Each answer must be exactly \"True\" or \"False\". Do NOT generate MCQ or Short Answer.";
        }
        if ("LONG".equalsIgnoreCase(one)) {
            return "ALL " + count + " questions MUST be type LONG (long / constructed response). " +
                    "Use \"type\":\"LONG\" with a multi-sentence or bullet-point model \"answer\" (marking points). " +
                    "No MCQ options field.";
        }
        // Mixed: e.g. "MCQ, TF" or "MCQ, TF, SHORT, LONG"
        return "Use ONLY these types (as a mix across the " + count + " questions): " + one + ". " +
                "Do NOT default to all MCQ. Include a variety of the requested types. " +
                "For LONG, use no options array; give a model answer with marking points.";
    }

    /**
     * When the teacher selected a single type, show only that JSON shape — listing SHORT/TF/LONG
     * examples alongside MCQ causes the model to mix types despite instructions.
     */
    private String buildTypeJsonFormatInstructions(String questionTypes, int count, boolean includePerQuestionExplanations) {
        String exTail = includePerQuestionExplanations
                ? ", \"explanation\": \"1-3 short teacher-facing sentences only\""
                : "";
        String raw = questionTypes == null ? "" : questionTypes.trim();
        if (raw.isEmpty()) {
            raw = "MCQ";
        }
        if (!raw.contains(",") && !raw.contains("，")) {
            String only = raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
            if ("MCQ".equals(only)) {
                return "JSON shape — repeat for all " + count + " items. Forbidden types: SHORT, LONG, TF.\n"
                        + "{\"type\":\"MCQ\", \"text\":\"...\", \"options\":[\"...\",\"...\",\"...\",\"...\"], "
                        + "\"answer\":\"must equal one of the four options\" " + exTail + " }";
            }
            if ("SHORT".equals(only)) {
                return "JSON shape — repeat for all " + count + " items. Do not use MCQ, LONG, or TF.\n"
                        + "{\"type\":\"SHORT\", \"text\":\"...\", \"answer\":\"...\" " + exTail + " }";
            }
            if ("TF".equals(only)) {
                return "JSON shape — repeat for all " + count + " items. Do not use MCQ, SHORT, or LONG.\n"
                        + "{\"type\":\"TF\", \"text\":\"...\", \"answer\":\"...\" " + exTail
                        + " } — the answer value must be exactly the string True or the string False.";
            }
            if ("LONG".equals(only)) {
                return "JSON shape — repeat for all " + count + " items. Do not use MCQ, SHORT, or TF.\n"
                        + "{\"type\":\"LONG\", \"text\":\"...\", \"answer\":\"...\" " + exTail + " }";
            }
        }
        String longEx = includePerQuestionExplanations
                ? "{\"type\":\"LONG\", \"text\":\"...\", \"answer\":\"...\", \"explanation\":\"...\"}"
                : "{\"type\":\"LONG\", \"text\":\"...\", \"answer\":\"...\"}";
        return "Each array item may use one of these shapes; match the type mix the teacher asked for:\n"
                + "SHORT: {\"type\":\"SHORT\", \"text\":\"...\", \"answer\":\"...\" " + exTail + " }\n"
                + "TF: {\"type\":\"TF\", \"text\":\"...\", \"answer\":\"...\" " + exTail + " }\n"
                + "MCQ: {\"type\":\"MCQ\", \"text\":\"...\", \"options\":[\"A\",\"B\",\"C\",\"D\"], \"answer\":\"...\" " + exTail + " }\n"
                + "LONG: " + longEx;
    }

    /** When a single type is selected, do not list other types' field rules in the prompt. */
    private String buildTypeFieldRules(String questionTypes) {
        String raw = questionTypes == null ? "" : questionTypes.trim();
        if (raw.isEmpty()) {
            raw = "MCQ";
        }
        if (!raw.contains(",") && !raw.contains("，")) {
            String only = raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
            if ("MCQ".equals(only)) {
                return "- MCQ: exactly 4 options, answer must match one option";
            }
            if ("SHORT".equals(only)) {
                return "- SHORT: no options; answer is a short phrase (1-6 words)";
            }
            if ("TF".equals(only)) {
                return "- TF: answer must be exactly \"True\" or \"False\"";
            }
            if ("LONG".equals(only)) {
                return "- LONG: no options; \"answer\" = model marking response";
            }
        }
        return """
                - SHORT: no options; answer is a short phrase (1-6 words)
                - TF: answer must be exactly "True" or "False"
                - MCQ: exactly 4 options, answer must match one option
                - LONG: no options; "answer" = model marking response""";
    }

    /**
     * When the teacher selected a single question type, drop any array elements with a different
     * {@code type} (models sometimes still emit SHORT/LONG in MCQ-only runs).
     */
    private String filterToSingleTypeIfNeeded(String questionsJson, String questionTypes) {
        String tRaw = questionTypes == null ? "" : questionTypes.trim();
        if (tRaw.isEmpty()) {
            tRaw = "MCQ";
        }
        if (tRaw.contains(",") || tRaw.contains("，")) {
            return questionsJson;
        }
        String only = tRaw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!"MCQ".equals(only) && !"SHORT".equals(only) && !"TF".equals(only) && !"LONG".equals(only)) {
            return questionsJson;
        }
        try {
            JsonNode root = mapper.readTree(questionsJson);
            if (root == null || !root.isArray()) {
                return questionsJson;
            }
            var out = mapper.createArrayNode();
            for (JsonNode n : root) {
                String elType = n.path("type").asText("").replaceAll("\\s", "").toUpperCase(Locale.ROOT);
                if (elType.equals(only)) {
                    out.add(n);
                }
            }
            if (out.size() < root.size()) {
                log.warn("Dropped {} off-type questions (request was {} only)", root.size() - out.size(), only);
            }
            return mapper.writeValueAsString(out);
        } catch (Exception e) {
            return questionsJson;
        }
    }

    private String difficultyInstruction(String difficulty) {
        if (difficulty == null || difficulty.isBlank() || "AUTO".equalsIgnoreCase(difficulty)) {
            return "AUTO: infer difficulty from the target grade — do NOT use a generic one-size-fits-all level.";
        }
        if ("EASY".equalsIgnoreCase(difficulty)) {
            return "EASY: one-step recall; short sentences; familiar vocabulary only; no multi-hop reasoning.";
        }
        if ("HARD".equalsIgnoreCase(difficulty)) {
            return "HARD: multi-step reasoning, compare/contrast, or apply concept to a new situation; still one clear path to the answer.";
        }
        return "MEDIUM: mix recall with short application; one or two reasoning steps max.";
    }

    /** Stronger calibration so questions do not all read like the same band. */
    private static String gradeBandInstruction(String grade) {
        if (grade == null || grade.isBlank()) {
            return """
                    Target grade: NOT specified — infer from topic/context if possible; otherwise use mid-primary (short words, concrete examples).
                    Do NOT default to generic “middle school” difficulty for every question.
                    """;
        }
        String g = grade.trim();
        return """
                Target grade (MANDATORY — vocabulary, sentence length, and concept depth MUST match this band):
                Grade %s — lower grades: shorter questions, simpler words, concrete examples; upper grades (9–12): allow tighter reasoning, technical terms where taught, multi-part stems only if appropriate for this grade.
                Every question must sound like it was written FOR this grade, not a generic quiz reused for all ages.
                """.formatted(g);
    }

    /**
     * Asks OpenAI to generate quiz questions from a topic or extracted text.
     * Returns a JSON string ready to store in questionsJson.
     */
    public String generateQuestionsJson(String topic, String supportingContext, int count, String questionTypes, String languagePreference) {
        return generateQuestionsJson(topic, supportingContext, count, questionTypes, languagePreference, "AUTO", "");
    }

    public String generateQuestionsJson(String topic, String supportingContext, int count, String questionTypes, String languagePreference, String difficulty) {
        return generateQuestionsJson(topic, supportingContext, count, questionTypes, languagePreference, difficulty, "");
    }

    public String generateQuestionsJson(String topic, String supportingContext, int count, String questionTypes, String languagePreference, String difficulty, String grade) {
        return generateQuestionsJson(topic, supportingContext, count, questionTypes, languagePreference, difficulty, grade, false);
    }

    public String generateQuestionsJson(String topic, String supportingContext, int count, String questionTypes, String languagePreference, String difficulty, String grade, boolean includePerQuestionExplanations) {
        if (topic == null || topic.isBlank()) {
            return "[]";
        }
        if (StrictEducationalGuard.isBlocked(topic)) {
            return "[]";
        }
        if (StrictEducationalGuard.isBlocked(supportingContext)) {
            return "[]";
        }
        String gradeParam = grade == null ? "" : grade.trim();
        int safeCount = Math.max(1, Math.min(count, 15));
        if (safeCount <= 10) {
            return generateQuestionsJsonSingle(topic, supportingContext, safeCount, questionTypes, languagePreference, difficulty, gradeParam, includePerQuestionExplanations);
        }
        // Large counts hallucinate more often in one-shot generation; generate in bounded batches.
        int remaining = safeCount;
        int batchSize = 8;
        java.util.List<JsonNode> merged = new java.util.ArrayList<>();
        try {
            while (remaining > 0) {
                int current = Math.min(batchSize, remaining);
                String batchRaw = generateQuestionsJsonSingle(topic, supportingContext, current, questionTypes, languagePreference, difficulty, gradeParam, includePerQuestionExplanations);
                JsonNode arr = mapper.readTree(batchRaw);
                if (arr != null && arr.isArray()) {
                    for (JsonNode n : arr) merged.add(n);
                }
                remaining -= current;
            }
            if (merged.size() > safeCount) {
                merged = merged.subList(0, safeCount);
            }
            return mapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.error("AI batched question generation failed: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * NCERT/page-window mode: generate questions ONLY from extracted content (no topic required).
     * This intentionally avoids the "canonical topic" lock, because the UI provides only content.
     */
    public String generateQuestionsJsonFromContentOnly(String supportingContext, int count, String questionTypes, String languagePreference, String difficulty, boolean includePerQuestionExplanations) {
        if (supportingContext == null || supportingContext.isBlank()) {
            return "[]";
        }
        // NOTE: Do NOT apply StrictEducationalGuard to extracted textbook content (e.g. NCERT).
        // The guard is intended for raw user queries; extracted passages can contain words that
        // accidentally match non-educational heuristics, which would incorrectly block quizzes.
        int safeCount = Math.max(1, Math.min(count, 15));
        String typeInstruction = buildTypeInstruction(questionTypes == null ? "MCQ" : questionTypes.trim(), safeCount);
        String languageRule = languageInstruction(languagePreference, false);
        String difficultyRule = difficultyInstruction(difficulty);
        String cleanContext = supportingContext == null ? "" : supportingContext.trim();
        String contextForPrompt = cleanContext.length() > 12000 ? cleanContext.substring(0, 12000) : cleanContext;
        String explanationBlock = includePerQuestionExplanations
                ? """
            TEACHER-FACING: Every object MUST also include: "explanation": "1-3 short sentences (why the answer is correct, or the key idea for marking). Not for the student handout in-class."
            """
                : "";
        String explanationRules = includePerQuestionExplanations
                ? "- Every item MUST include a non-empty \"explanation\" string."
                : "";
        String typeFormat = buildTypeJsonFormatInstructions(questionTypes, safeCount, includePerQuestionExplanations);
        String typeFieldRules = buildTypeFieldRules(questionTypes);

        String basePrompt = """
            You are an experienced school teacher in India.

            """ + EDUCATIONAL_ONLY_QUIZ + """

            """ + EducationalRedirectionPolicy.PROMPT_BLOCK + """

            Extracted textbook content (ONLY source of truth — do not invent facts beyond it):
            %s

            Generate exactly %d quiz questions grounded ONLY in the extracted content above.

            CRITICAL - QUESTION TYPES (you must follow this exactly):
            %s

            %s

            Respond ONLY with a valid JSON array, no markdown, no extra text.
            %s

            Rules:
            %s
            %s
            - Language: %s
            - Difficulty setting: %s
            """.formatted(
                contextForPrompt,
                safeCount,
                typeInstruction,
                explanationBlock,
                typeFormat,
                typeFieldRules,
                explanationRules,
                languageRule,
                difficultyRule
        );

        String[] attempts = new String[] {
                basePrompt,
                basePrompt + "\n\nIf the extracted content is short or narrative, ask comprehension/vocabulary questions from the same content. "
                        + "Do NOT refuse. Still output exactly " + safeCount + " questions.\n"
        };

        for (int attempt = 0; attempt < attempts.length; attempt++) {
            try {
                String raw = openAIClient.generateQuizCompletion(attempts[attempt]);
                if (raw == null) continue;
                raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
                if (raw.isEmpty()) continue;
                JsonNode parsed = mapper.readTree(raw);
                if (parsed == null || !parsed.isArray()) {
                    log.warn("AI content-only quiz generation returned non-array JSON (attempt {}); dropping.", attempt + 1);
                    continue;
                }
                if (parsed.isEmpty()) {
                    log.warn("AI content-only quiz generation returned empty array (attempt {}).", attempt + 1);
                    continue;
                }
                return filterToSingleTypeIfNeeded(raw, questionTypes);
            } catch (Exception e) {
                log.error("AI content-only quiz generation failed (attempt {}): {}", attempt + 1, e.getMessage());
            }
        }
        return "[]";
    }

    private String generateQuestionsJsonSingle(String topic, String supportingContext, int count, String questionTypes, String languagePreference, String difficulty, String grade, boolean includePerQuestionExplanations) {
        String typeInstruction = buildTypeInstruction(questionTypes.trim(), count);
        String languageRule = languageInstruction(languagePreference, false);
        String difficultyRule = difficultyInstruction(difficulty);
        String gradeRule = gradeBandInstruction(grade);
        String cleanTopic = topic == null ? "" : topic.trim();
        String cleanContext = supportingContext == null ? "" : supportingContext.trim();
        String contextForPrompt = cleanContext.length() > 6000 ? cleanContext.substring(0, 6000) : cleanContext;
        String explanationBlock = includePerQuestionExplanations
                ? """
            TEACHER-FACING: Every object MUST also include: "explanation": "1-3 short sentences (why the answer is correct, or the key idea for marking). Not for the student handout in-class."
            """
                : "";
        String explanationRules = includePerQuestionExplanations
                ? "- Every item MUST include a non-empty \"explanation\" string."
                : "";
        String typeFormat = buildTypeJsonFormatInstructions(questionTypes, count, includePerQuestionExplanations);
        String typeFieldRules = buildTypeFieldRules(questionTypes);
        String prompt = """
            You are an experienced school teacher in India.

            """ + EDUCATIONAL_ONLY_QUIZ + """

            """ + EducationalRedirectionPolicy.PROMPT_BLOCK + """

            Canonical quiz topic (MANDATORY scope): "%s"

            Supporting classroom context (optional, for factual grounding only):
            %s

            %s

            Generate exactly %d quiz questions ONLY about the canonical quiz topic.
            Do NOT switch to any other topic even if supporting context contains extra details.

            CRITICAL - QUESTION TYPES (you must follow this exactly):
            %s

            %s

            Respond ONLY with a valid JSON array, no markdown, no extra text.
            %s

            Rules:
            %s
            %s
            - Be STRICTLY grounded in the canonical topic and supporting context. Do not invent names, years, numbers, or facts not present or commonly taught basics.
            - If supporting context is limited, ask easier concept/comprehension questions within the same canonical topic.
            - Language: %s
            - Difficulty setting: %s
            """.formatted(
                cleanTopic, contextForPrompt, gradeRule, count, typeInstruction, explanationBlock,
                typeFormat, typeFieldRules, explanationRules, languageRule, difficultyRule);

        try {
            String raw = openAIClient.generateQuizCompletion(prompt);
            if (raw == null) return "[]";
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            if (raw.isEmpty()) return "[]";
            // Validate it's a JSON array (models sometimes respond with an object despite instructions)
            JsonNode parsed = mapper.readTree(raw);
            if (parsed == null || !parsed.isArray()) {
                log.warn("AI quiz generation returned non-array JSON; dropping.");
                return "[]";
            }
            return filterToSingleTypeIfNeeded(raw, questionTypes);
        } catch (Exception e) {
            log.error("AI question generation failed: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Screenshot/image-based quiz generation.
     * If topic/grade/description are missing, the model infers them from image.
     */
    public record GeneratedImageQuiz(String topic, String grade, String description, String questionsJson, String error) {}

    public GeneratedImageQuiz generateQuestionsFromImage(String topic, String grade, String description, int count, String questionTypes, String languagePreference, String imageDataUri) {
        return generateQuestionsFromImage(topic, grade, description, count, questionTypes, languagePreference, "AUTO", false, imageDataUri);
    }

    public GeneratedImageQuiz generateQuestionsFromImage(String topic, String grade, String description, int count, String questionTypes, String languagePreference, String difficulty, String imageDataUri) {
        return generateQuestionsFromImage(topic, grade, description, count, questionTypes, languagePreference, difficulty, false, imageDataUri);
    }

    public GeneratedImageQuiz generateQuestionsFromImage(String topic, String grade, String description, int count, String questionTypes, String languagePreference, String difficulty, boolean includePerQuestionExplanations, String imageDataUri) {
        String combinedHints = (topic == null ? "" : topic) + " " + (description == null ? "" : description);
        if (StrictEducationalGuard.isBlocked(combinedHints)) {
            return new GeneratedImageQuiz("", "", "", "[]", StrictEducationalGuard.refusalMessage());
        }
        int safeCount = Math.max(1, Math.min(count, 15));
        String typeInstruction = buildTypeInstruction(questionTypes.trim(), safeCount);
        String languageRule = languageInstruction(languagePreference, true);
        String difficultyRule = difficultyInstruction(difficulty);
        String gradeHint = grade == null ? "" : grade.trim();
        String gradeRule = gradeBandInstruction(gradeHint);
        String exImg = includePerQuestionExplanations
                ? "Each questionsJson object MUST also include: \"explanation\": \"1-3 teacher-facing sentences (why the answer is correct; marking hint).\".\n"
                : "";
        String typeFormat = buildTypeJsonFormatInstructions(questionTypes, safeCount, includePerQuestionExplanations);
        String typeFieldRules = buildTypeFieldRules(questionTypes);
        String prompt = """
            You are an experienced school teacher in India.

            """ + EDUCATIONAL_ONLY_QUIZ + """

            """ + EducationalRedirectionPolicy.PROMPT_BLOCK + """

            Use the attached classroom screenshot/textbook image to infer the subject/topic and likely grade level.
            Teacher-provided hints (may be empty):
            - Topic hint: %s
            - Grade hint: %s
            - Description hint: %s

            Infer topic, grade, and short description from the screenshot if teacher hints are empty.
            Then generate exactly %d quiz questions.

            %s

            CRITICAL - QUESTION TYPES (you must follow this exactly):
            %s

            %s
            First decide if the screenshot is educational/classroom content.
            - If NOT educational, do not generate any questions.
            - Return educational=false and a short refusal reason.
            - If educational, return educational=true with inferred metadata and questions.

            Respond ONLY with a valid JSON object, no markdown, no extra text:
            {
              "educational": true/false,
              "refusalReason": "string (empty when educational=true)",
              "topic": "string",
              "grade": "string",
              "description": "string",
              "questionsJson": [ ... ]
            }

            %s

            Rules:
            - Questions must be clearly grounded in the image content.
            - Detect script from screenshot text first.
            - If ANY visible screenshot text is in Devanagari, generate ALL questions, options, and answers in Hindi only (no English mix).
            - If screenshot text is clearly English only, generate in English unless teacher selected Hindi.
            - If image text is unclear, create safe foundational questions from the closest visible topic.
            %s
            - Language: %s
            - Difficulty setting: %s
            """.formatted(
                topic == null ? "" : topic.trim(),
                gradeHint,
                description == null ? "" : description.trim(),
                safeCount,
                gradeRule,
                typeInstruction,
                exImg,
                typeFormat,
                typeFieldRules,
                languageRule,
                difficultyRule
        );

        try {
            String raw = openAIClient.generateQuizCompletionFromImage(prompt, imageDataUri);
            if (raw == null) return new GeneratedImageQuiz("", "", "", "[]", "Generation failed.");
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            if (raw.isEmpty()) return new GeneratedImageQuiz("", "", "", "[]", "Generation failed.");
            JsonNode n = mapper.readTree(raw);
            boolean educational = n.path("educational").asBoolean(true);
            String refusalReason = n.path("refusalReason").asText("");
            if (!educational) {
                String msg = (refusalReason == null || refusalReason.isBlank())
                        ? "Uploaded screenshot is not educational. Quiz generation is blocked."
                        : refusalReason.trim();
                return new GeneratedImageQuiz("", "", "", "[]", msg);
            }
            JsonNode questions = n.path("questionsJson");
            String questionsJson = (questions != null && questions.isArray())
                    ? mapper.writeValueAsString(questions)
                    : "[]";
            questionsJson = filterToSingleTypeIfNeeded(questionsJson, questionTypes);
            String inferredTopic = n.path("topic").asText("");
            String inferredGrade = n.path("grade").asText("");
            String inferredDescription = n.path("description").asText("");
            return new GeneratedImageQuiz(inferredTopic, inferredGrade, inferredDescription, questionsJson, "");
        } catch (Exception e) {
            log.error("AI image quiz generation failed: {}", e.getMessage());
            return new GeneratedImageQuiz("", "", "", "[]", "Generation failed.");
        }
    }

    // ── Prompt-style quiz generation (single prompt → title/grade/subject + questions) ──

    public record GeneratedQuiz(String title, String subject, String grade, String questionsJson) {}
    public record InferredQuizMeta(String topic, String grade, String description) {}

    /**
     * Teacher gives a single free-form prompt like:
     * "Grade 5 photosynthesis, 10 questions MCQ+TF"
     *
     * We interpret it using one LLM call and return:
     * - title (required by QuizEntity)
     * - subject, grade (best-effort)
     * - questionsJson: a JSON array string in the same format expected by the existing editor
     */
    public GeneratedQuiz generateQuizFromPrompt(String promptOrText) {
        if (openAIClient.classifyUserQuery(promptOrText) != OpenAIClient.QueryCategory.LEARNING) {
            return new GeneratedQuiz("Quiz", "", "", "[]");
        }
        if (StrictEducationalGuard.isBlocked(promptOrText)) {
            return new GeneratedQuiz("Quiz", "", "", "[]");
        }
        String prompt = """
            You are an experienced school teacher in India.

            """ + EDUCATIONAL_ONLY_QUIZ + """

            """ + EducationalRedirectionPolicy.PROMPT_BLOCK + """

            If the teacher prompt is superficial (gossip, ratings of people, comparisons of looks), interpret it as a request for a quiz on a pivoted educational topic only — never include questions about attractiveness or gossip.

            The teacher provides a SINGLE prompt describing what quiz to create for a class.
            From it, extract:
              - grade (string or null)
              - subject (string or null)
              - topic (string)
              - count (int, default 5 if missing)
              - questionTypes (MCQ/TF/SHORT, default "MCQ, TF" if missing)

            Then generate EXACTLY count questions about the topic.
            Question JSON rules (must follow exactly):
              - MCQ:
                {"type":"MCQ","text":"Question?","options":["A","B","C","D"],"answer":"A"} (answer must be one of the options exactly)
              - TF:
                {"type":"TF","text":"Statement.","answer":"True"} or "False"
              - SHORT:
                {"type":"SHORT","text":"Question?","answer":"Expected short answer"} (1-6 words, no options)

            Language: use English unless the topic is clearly written in Hindi (Devanagari characters).

            Return ONLY a single valid JSON object (no markdown, no extra text) with this exact structure:
            {
              "title": "string (required)",
              "subject": "string or null",
              "grade": "string or null",
              "questionsJson": [ ... questions array ... ]
            }

            Title rules:
              - If grade exists: "Quiz (Grade <grade>): <topic>"
              - Else: "Quiz: <topic>"

            Teacher prompt:
            %s
            """.formatted(promptOrText == null ? "" : promptOrText.trim());

        try {
            String raw = openAIClient.generateQuizCompletion(prompt);
            if (raw == null || raw.isBlank()) return new GeneratedQuiz("Quiz", "", "", "[]");
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();

            JsonNode n = mapper.readTree(raw);
            String title   = n.path("title").asText("Quiz");
            String subject = n.path("subject").isMissingNode() || n.path("subject").isNull() ? "" : n.path("subject").asText("");
            String grade   = n.path("grade").isMissingNode() || n.path("grade").isNull() ? "" : n.path("grade").asText("");

            JsonNode questions = n.path("questionsJson");
            String questionsJson = (questions != null && questions.isArray())
                    ? mapper.writeValueAsString(questions)
                    : "[]";

            return new GeneratedQuiz(title, subject, grade, questionsJson);
        } catch (Exception e) {
            log.error("Prompt-style quiz generation failed: {}", e.getMessage());
            return new GeneratedQuiz("Quiz", "", "", "[]");
        }
    }

    /**
     * Infer topic/grade/description from extracted source text (e.g., NCERT URL content).
     */
    public InferredQuizMeta inferQuizMetaFromContext(String contextText) {
        if (contextText == null || contextText.isBlank()) {
            return new InferredQuizMeta("", "", "");
        }
        // Be permissive here: this is only metadata inference (topic/grade/description),
        // and overly-strict gating (especially for non-English scripts) causes empty titles in UI.
        if (openAIClient.classifyUserQuery(contextText) == OpenAIClient.QueryCategory.UNSAFE) {
            return new InferredQuizMeta("", "", "");
        }
        String prompt = """
            You are helping a teacher create a school quiz.
            Infer metadata from the content below.

            Return ONLY valid JSON:
            {
              "topic":"string",
              "grade":"string",
              "description":"string"
            }

            Rules:
            - topic: short and specific (2-8 words)
            - grade: one of 0-12 when inferable, else empty string
            - description: one concise line suitable for teacher form
            - Keep language consistent with source content (Hindi source -> Hindi output)

            Source content:
            %s
            """.formatted(contextText.length() > 5000 ? contextText.substring(0, 5000) : contextText);
        try {
            String raw = openAIClient.generateQuizCompletion(prompt);
            if (raw == null || raw.isBlank()) return new InferredQuizMeta("", "", "");
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            JsonNode n = mapper.readTree(raw);
            return new InferredQuizMeta(
                    n.path("topic").asText(""),
                    n.path("grade").asText(""),
                    n.path("description").asText("")
            );
        } catch (Exception e) {
            log.warn("Could not infer quiz metadata from source content: {}", e.getMessage());
            return new InferredQuizMeta("", "", "");
        }
    }

    // ── Quiz response APIs ────────────────────────────────────────────────────

    public record AssignStudentRequest(Long questionId, String studentId) {}
    public record SubmitAnswerRequest(Long questionId, String studentId, String answer) {}
    public record QuestionResultRow(
            Long questionId,
            String questionText,
            String studentId,
            String studentName,
            String answer,
            String correctAnswer,
            Integer marksAwarded,
            Boolean isCorrect,
            String explanation
    ) {}

    public record QuestionData(
            Long id, String type, String text, List<String> options, String answer, String explanation, String watermarkUrl) {}

    private List<QuestionData> parseQuestions(String questionsJson) {
        List<QuestionData> out = new ArrayList<>();
        if (questionsJson == null || questionsJson.isBlank()) return out;
        try {
            JsonNode arr = mapper.readTree(questionsJson);
            if (!arr.isArray()) return out;
            for (JsonNode n : arr) {
                String type = n.path("type").asText("").trim().toUpperCase(Locale.ROOT);
                String text = n.path("text").asText("").trim();
                String answer = n.path("answer").asText("").trim();
                String expl = n.path("explanation").asText("").trim();
                if (expl.length() > 2000) {
                    expl = expl.substring(0, 2000);
                }
                List<String> options = new ArrayList<>();
                if ("MCQ".equals(type) && n.path("options").isArray()) {
                    for (JsonNode o : n.path("options")) {
                        options.add(o.asText("").trim());
                    }
                }
                String wm = n.path("watermarkUrl").asText("").trim();
                out.add(new QuestionData(null, type, text, options, answer, expl.isEmpty() ? null : expl, wm.isEmpty() ? null : wm));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid questions payload.");
        }
    }

    private void saveQuestions(Long quizId, List<QuestionData> questions, String quizCoverUrl) {
        if (quizId == null) return;
        questionRepo.deleteByQuizId(quizId);
        int order = 0;
        for (QuestionData q : questions) {
            int ord = order++;
            QuizQuestionEntity e = new QuizQuestionEntity();
            e.setQuizId(quizId);
            e.setQuestionOrder(ord);
            e.setQuestionType(q.type() == null ? "" : q.type().trim().toUpperCase(Locale.ROOT));
            e.setQuestionText(q.text() == null ? "" : q.text().trim());
            List<String> opts = q.options() == null ? List.of() : q.options();
            e.setOptionA(opts.size() > 0 ? (opts.get(0) == null ? null : opts.get(0).trim()) : null);
            e.setOptionB(opts.size() > 1 ? (opts.get(1) == null ? null : opts.get(1).trim()) : null);
            e.setOptionC(opts.size() > 2 ? (opts.get(2) == null ? null : opts.get(2).trim()) : null);
            e.setOptionD(opts.size() > 3 ? (opts.get(3) == null ? null : opts.get(3).trim()) : null);
            e.setCorrectAnswer(q.answer() == null ? "" : q.answer().trim());
            e.setAnswerExplanation(q.explanation() == null || q.explanation().isBlank() ? null : q.explanation().trim());
            e.setMarks(1);
            String wm = q.watermarkUrl();
            if (wm == null || wm.isBlank()) {
                if (quizCoverUrl != null && !quizCoverUrl.isBlank()) {
                    wm = quizCoverUrl.trim();
                } else {
                    wm = buildPicsumWatermarkUrl(quizId, ord);
                }
            }
            e.setWatermarkImageUrl(wm.trim());
            questionRepo.save(e);
        }
    }

    @Transactional
    public QuestionResponseEntity assignStudentToQuestion(Long questionId, String studentId) {
        QuizQuestionEntity question = questionRepo.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        QuizEntity quiz = quizRepo.findById(question.getQuizId())
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found: " + question.getQuizId()));
        if (quiz.isLocked()) throw new IllegalStateException("Quiz is locked.");
        if (studentId == null || studentId.trim().isBlank()) {
            throw new IllegalArgumentException("studentId is required.");
        }
        StudentEntity student = studentRepo.findByCode(studentId.trim());
        if (student == null) throw new IllegalArgumentException("Student not found for id: " + studentId);

        try {
            QuestionResponseEntity response = questionResponseRepo.findByQuestionIdAndStudentId(questionId, student.getCode())
                    .orElseGet(QuestionResponseEntity::new);
            response.setQuestionId(questionId);
            response.setStudentId(student.getCode());
            // Re-assignment should clear any previous answer for this question for this student.
            response.setAnswer(null);
            response.setIsCorrect(null);
            response.setMarksAwarded(null);
            response.setAnsweredAt(null);
            return questionResponseRepo.save(response);
        } catch (DataIntegrityViolationException ex) {
            // Handles concurrent insert race on UNIQUE(question_id, student_id): fallback to update existing row.
            QuestionResponseEntity existing = questionResponseRepo.findByQuestionIdAndStudentId(questionId, student.getCode())
                    .orElseThrow(() -> new IllegalStateException("Could not assign student due to concurrent write."));
            existing.setAnswer(null);
            existing.setIsCorrect(null);
            existing.setMarksAwarded(null);
            existing.setAnsweredAt(null);
            return questionResponseRepo.save(existing);
        }
    }

    @Transactional
    public QuestionResponseEntity submitAnswer(Long questionId, String studentId, String answer) {
        QuizQuestionEntity question = questionRepo.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        QuizEntity quiz = quizRepo.findById(question.getQuizId())
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found: " + question.getQuizId()));
        if (quiz.isLocked()) throw new IllegalStateException("Quiz is locked.");

        QuestionResponseEntity response = questionResponseRepo.findByQuestionIdAndStudentId(questionId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Question is not assigned to this student."));

        String normalizedAnswer = answer == null ? "" : answer.trim();
        String correct = question.getCorrectAnswer() == null ? "" : question.getCorrectAnswer().trim();
        boolean isCorrect = !normalizedAnswer.isBlank() && correct.equalsIgnoreCase(normalizedAnswer);
        int marks = question.getMarks() == null ? 1 : question.getMarks();

        response.setAnswer(normalizedAnswer);
        response.setIsCorrect(isCorrect);
        response.setMarksAwarded(isCorrect ? marks : 0);
        response.setAnsweredAt(java.time.Instant.now());
        return questionResponseRepo.save(response);
    }

    @Transactional
    public void lockQuiz(Long quizId) {
        QuizEntity quiz = quizRepo.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found: " + quizId));
        quiz.setLocked(true);
        quizRepo.save(quiz);
    }

    @Transactional(readOnly = true)
    public List<QuestionResultRow> getQuizQuestionResults(Long quizId) {
        return questionResponseRepo.findForQuizWithQuestionAndStudent(quizId).stream()
                .map(r -> {
                    String questionType = r.getQuestion() != null && r.getQuestion().getQuestionType() != null
                            ? r.getQuestion().getQuestionType().trim().toUpperCase(Locale.ROOT)
                            : "";
                    String correct = r.getQuestion() != null && r.getQuestion().getCorrectAnswer() != null
                            ? r.getQuestion().getCorrectAnswer()
                            : "";
                    String given = r.getAnswer() == null ? "" : r.getAnswer();
                    boolean right = Boolean.TRUE.equals(r.getIsCorrect());
                    return new QuestionResultRow(
                            r.getQuestionId(),
                            r.getQuestion() != null ? r.getQuestion().getQuestionText() : "",
                            r.getStudentId(),
                            r.getStudent() != null ? r.getStudent().getName() : r.getStudentId(),
                            given,
                            correct,
                            r.getMarksAwarded(),
                            r.getIsCorrect(),
                            buildAnswerExplanation(questionType, correct, given, right)
                    );
                })
                .toList();
    }

    private List<String> buildOptions(QuizQuestionEntity q) {
        List<String> out = new ArrayList<>();
        if (q.getOptionA() != null && !q.getOptionA().isBlank()) out.add(q.getOptionA());
        if (q.getOptionB() != null && !q.getOptionB().isBlank()) out.add(q.getOptionB());
        if (q.getOptionC() != null && !q.getOptionC().isBlank()) out.add(q.getOptionC());
        if (q.getOptionD() != null && !q.getOptionD().isBlank()) out.add(q.getOptionD());
        return out;
    }

    private String buildAnswerExplanation(String qType, String correct, String given, boolean isRight) {
        String safeGiven = (given == null || given.isBlank()) ? "no answer" : "\"" + given + "\"";
        String safeCorrect = (correct == null || correct.isBlank()) ? "the expected answer" : "\"" + correct + "\"";
        if (isRight) {
            return "Correct. The expected answer is " + safeCorrect + ", and the student answered " + safeGiven + ".";
        }
        if ("TF".equals(qType)) {
            return "Incorrect. This True/False statement should be " + safeCorrect + ".";
        }
        if ("MCQ".equals(qType)) {
            return "Incorrect. The best option is " + safeCorrect + "; student selected " + safeGiven + ".";
        }
        return "Incorrect. Expected " + safeCorrect + " but received " + safeGiven + ".";
    }

    private String languageInstruction(String preference, boolean imageFlow) {
        String p = preference == null ? "AUTO" : preference.trim().toUpperCase();
        if ("HINDI".equals(p)) {
            return "Generate all questions/options/answers in simple Hindi (Devanagari script).";
        }
        if ("ENGLISH".equals(p)) {
            return "Generate all questions/options/answers in simple English.";
        }
        if ("SANSKRIT".equals(p)) {
            return "Generate all questions/options/answers in simple Sanskrit (Devanagari script). Use clear, student-friendly Sanskrit (avoid very complex compounds).";
        }
        if (imageFlow) {
            return "AUTO: First inspect screenshot text script. If any Devanagari text appears, force Hindi output for every field; otherwise use English.";
        }
        return "AUTO: English unless the provided topic/description is clearly Hindi.";
    }
}

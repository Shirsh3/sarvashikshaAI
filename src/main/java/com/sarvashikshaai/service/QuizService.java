package com.sarvashikshaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.EducationalRedirectionPolicy;
import com.sarvashikshaai.ai.OpenAIClient;
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
        List<QuestionData> questions = parseQuestions(questionsJson);
        int count = questions.size();
        String cleanTopic = topic != null ? topic.trim() : "";
        if (cleanTopic.isBlank()) cleanTopic = "General";
        String cleanGrade = grade != null ? grade.trim() : "";
        String cleanDescription = description != null ? description.trim() : "";
        String title = cleanGrade.isBlank()
                ? "Quiz: " + cleanTopic
                : "Quiz (Grade " + cleanGrade + "): " + cleanTopic;
        QuizEntity quiz = quizRepo.save(new QuizEntity(
                title,
                cleanTopic,        // subject (legacy)
                cleanTopic,        // topic
                cleanGrade,
                cleanDescription,
                count
        ));
        saveQuestions(quiz.getId(), questions);
        return quiz;
    }

    public List<QuestionData> getQuestionsByQuiz(Long quizId) {
        return questionRepo.findByQuizIdOrderByQuestionOrderAsc(quizId).stream()
                .map(q -> new QuestionData(
                        q.getId(),
                        q.getQuestionType(),
                        q.getQuestionText(),
                        buildOptions(q),
                        q.getCorrectAnswer()
                ))
                .toList();
    }

    // ── AI question generation ────────────────────────────────────────────────

    private String buildTypeInstruction(String questionTypes, int count) {
        if ("SHORT".equalsIgnoreCase(questionTypes)) {
            return "ALL " + count + " questions MUST be type SHORT (Short Answer). Do NOT generate any MCQ or True/False. Every item in the JSON array must have \"type\":\"SHORT\" with \"text\" and \"answer\" only (no options).";
        }
        if ("MCQ".equalsIgnoreCase(questionTypes)) {
            return "ALL " + count + " questions MUST be type MCQ (Multiple Choice). Each must have exactly 4 options and an answer matching one option. Do NOT generate True/False or Short Answer.";
        }
        if ("TF".equalsIgnoreCase(questionTypes)) {
            return "ALL " + count + " questions MUST be type TF (True/False). Each answer must be exactly \"True\" or \"False\". Do NOT generate MCQ or Short Answer.";
        }
        // Mixed: e.g. "MCQ, TF" or "MCQ, TF, SHORT"
        return "Use ONLY these types (as a mix across the " + count + " questions): " + questionTypes + ". Do NOT default to all MCQ. Include a variety of the requested types.";
    }

    private String difficultyInstruction(String difficulty) {
        if (difficulty == null || difficulty.isBlank() || "AUTO".equalsIgnoreCase(difficulty)) {
            return "AUTO (adapt to detected grade/content; keep school-appropriate progression).";
        }
        if ("EASY".equalsIgnoreCase(difficulty)) {
            return "EASY: direct recall/basic understanding; simple wording; minimal multi-step reasoning.";
        }
        if ("HARD".equalsIgnoreCase(difficulty)) {
            return "HARD: higher-order application/reasoning; avoid trivia; still age-appropriate.";
        }
        return "MEDIUM: balanced conceptual and application-level questions.";
    }

    /**
     * Asks OpenAI to generate quiz questions from a topic or extracted text.
     * Returns a JSON string ready to store in questionsJson.
     */
    public String generateQuestionsJson(String topicOrText, int count, String questionTypes, String languagePreference) {
        return generateQuestionsJson(topicOrText, count, questionTypes, languagePreference, "AUTO");
    }

    public String generateQuestionsJson(String topicOrText, int count, String questionTypes, String languagePreference, String difficulty) {
        int safeCount = Math.max(1, Math.min(count, 15));
        if (safeCount <= 10) {
            return generateQuestionsJsonSingle(topicOrText, safeCount, questionTypes, languagePreference, difficulty);
        }
        // Large counts hallucinate more often in one-shot generation; generate in bounded batches.
        int remaining = safeCount;
        int batchSize = 8;
        java.util.List<JsonNode> merged = new java.util.ArrayList<>();
        try {
            while (remaining > 0) {
                int current = Math.min(batchSize, remaining);
                String batchRaw = generateQuestionsJsonSingle(topicOrText, current, questionTypes, languagePreference, difficulty);
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

    private String generateQuestionsJsonSingle(String topicOrText, int count, String questionTypes, String languagePreference, String difficulty) {
        String typeInstruction = buildTypeInstruction(questionTypes.trim(), count);
        String languageRule = languageInstruction(languagePreference, false);
        String difficultyRule = difficultyInstruction(difficulty);
        String prompt = """
            You are an experienced school teacher in India.

            """ + EDUCATIONAL_ONLY_QUIZ + """

            """ + EducationalRedirectionPolicy.PROMPT_BLOCK + """

            If the topic or file text is superficial (gossip, ratings of people, comparisons of looks), generate questions ONLY on a pivoted educational topic (e.g. storytelling in film, costumes, media literacy) — never on ratings or attractiveness.

            Generate exactly %d quiz questions about: "%s"

            CRITICAL - QUESTION TYPES (you must follow this exactly):
            %s

            Respond ONLY with a valid JSON array, no markdown, no extra text.
            For SHORT type use this format only (no "options" field):
            {"type":"SHORT", "text":"Question?", "answer":"Expected short answer"}
            For TF type: {"type":"TF", "text":"Statement.", "answer":"True"} or answer "False"
            For MCQ type: {"type":"MCQ", "text":"Question?", "options":["A","B","C","D"], "answer":"A"}

            Rules:
            - SHORT: no options array; answer is a short phrase (1-6 words)
            - TF: answer must be exactly "True" or "False"
            - MCQ: exactly 4 options, answer must match one option exactly
            - Be STRICTLY grounded in the provided topic/text. Do not invent names, years, numbers, or facts not present or commonly taught basics.
            - If source text is limited, ask easier comprehension/concept questions from available content rather than hallucinating new facts.
            - Language: %s
            - Difficulty: %s
            - Level: suitable for school students aged 8-16
            """.formatted(count, topicOrText, typeInstruction, languageRule, difficultyRule);

        try {
            String raw = openAIClient.generateQuizCompletion(prompt);
            if (raw == null) return "[]";
            raw = raw.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            if (raw.isEmpty()) return "[]";
            // Validate it's a JSON array
            mapper.readTree(raw);
            return raw;
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
        return generateQuestionsFromImage(topic, grade, description, count, questionTypes, languagePreference, "AUTO", imageDataUri);
    }

    public GeneratedImageQuiz generateQuestionsFromImage(String topic, String grade, String description, int count, String questionTypes, String languagePreference, String difficulty, String imageDataUri) {
        int safeCount = Math.max(1, Math.min(count, 15));
        String typeInstruction = buildTypeInstruction(questionTypes.trim(), safeCount);
        String languageRule = languageInstruction(languagePreference, true);
        String difficultyRule = difficultyInstruction(difficulty);
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

            CRITICAL - QUESTION TYPES (you must follow this exactly):
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

            For SHORT type use this format only (no "options" field):
            {"type":"SHORT", "text":"Question?", "answer":"Expected short answer"}
            For TF type: {"type":"TF", "text":"Statement.", "answer":"True"} or answer "False"
            For MCQ type: {"type":"MCQ", "text":"Question?", "options":["A","B","C","D"], "answer":"A"}

            Rules:
            - Questions must be clearly grounded in the image content.
            - Detect script from screenshot text first.
            - If ANY visible screenshot text is in Devanagari, generate ALL questions, options, and answers in Hindi only (no English mix).
            - If screenshot text is clearly English only, generate in English unless teacher selected Hindi.
            - If image text is unclear, create safe foundational questions from the closest visible topic.
            - SHORT: no options array; answer is a short phrase (1-6 words)
            - TF: answer must be exactly "True" or "False"
            - MCQ: exactly 4 options, answer must match one option exactly
            - Language: %s
            - Difficulty: %s
            - Level: suitable for school students aged 8-16
            """.formatted(
                topic == null ? "" : topic.trim(),
                grade == null ? "" : grade.trim(),
                description == null ? "" : description.trim(),
                safeCount,
                typeInstruction,
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
    public record QuestionResultRow(Long questionId, String questionText, String studentId, String studentName, String answer, Integer marksAwarded, Boolean isCorrect) {}

    public record QuestionData(Long id, String type, String text, List<String> options, String answer) {}

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
                List<String> options = new ArrayList<>();
                if ("MCQ".equals(type) && n.path("options").isArray()) {
                    for (JsonNode o : n.path("options")) {
                        options.add(o.asText("").trim());
                    }
                }
                out.add(new QuestionData(null, type, text, options, answer));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid questions payload.");
        }
    }

    private void saveQuestions(Long quizId, List<QuestionData> questions) {
        if (quizId == null) return;
        questionRepo.deleteByQuizId(quizId);
        int order = 0;
        for (QuestionData q : questions) {
            QuizQuestionEntity e = new QuizQuestionEntity();
            e.setQuizId(quizId);
            e.setQuestionOrder(order++);
            e.setQuestionType(q.type() == null ? "" : q.type().trim().toUpperCase(Locale.ROOT));
            e.setQuestionText(q.text() == null ? "" : q.text().trim());
            List<String> opts = q.options() == null ? List.of() : q.options();
            e.setOptionA(opts.size() > 0 ? opts.get(0) : null);
            e.setOptionB(opts.size() > 1 ? opts.get(1) : null);
            e.setOptionC(opts.size() > 2 ? opts.get(2) : null);
            e.setOptionD(opts.size() > 3 ? opts.get(3) : null);
            e.setCorrectAnswer(q.answer() == null ? "" : q.answer().trim());
            e.setMarks(1);
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
            QuestionResponseEntity response = questionResponseRepo.findByQuestionId(questionId)
                    .orElseGet(QuestionResponseEntity::new);
            response.setQuestionId(questionId);
            response.setStudentId(student.getCode());
            // Re-assignment should clear any previous answer for this question.
            response.setAnswer(null);
            response.setIsCorrect(null);
            response.setMarksAwarded(null);
            response.setAnsweredAt(null);
            return questionResponseRepo.save(response);
        } catch (DataIntegrityViolationException ex) {
            // Handles concurrent insert race on UNIQUE(question_id): fallback to update existing row.
            QuestionResponseEntity existing = questionResponseRepo.findByQuestionId(questionId)
                    .orElseThrow(() -> new IllegalStateException("Could not assign student due to concurrent write."));
            existing.setStudentId(student.getCode());
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

        QuestionResponseEntity response = questionResponseRepo.findByQuestionId(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question is not assigned to any student."));
        if (!response.getStudentId().equals(studentId)) {
            throw new IllegalArgumentException("Question is assigned to a different student.");
        }

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

    public List<QuestionResultRow> getQuizQuestionResults(Long quizId) {
        return questionResponseRepo.findByQuestion_QuizIdOrderByQuestion_QuestionOrderAsc(quizId).stream()
                .map(r -> new QuestionResultRow(
                        r.getQuestionId(),
                        r.getQuestion() != null ? r.getQuestion().getQuestionText() : "",
                        r.getStudentId(),
                        r.getStudent() != null ? r.getStudent().getName() : r.getStudentId(),
                        r.getAnswer(),
                        r.getMarksAwarded(),
                        r.getIsCorrect()
                ))
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

    private String languageInstruction(String preference, boolean imageFlow) {
        String p = preference == null ? "AUTO" : preference.trim().toUpperCase();
        if ("HINDI".equals(p)) {
            return "Generate all questions/options/answers in simple Hindi (Devanagari script).";
        }
        if ("ENGLISH".equals(p)) {
            return "Generate all questions/options/answers in simple English.";
        }
        if (imageFlow) {
            return "AUTO: First inspect screenshot text script. If any Devanagari text appears, force Hindi output for every field; otherwise use English.";
        }
        return "AUTO: English unless the provided topic/description is clearly Hindi.";
    }
}

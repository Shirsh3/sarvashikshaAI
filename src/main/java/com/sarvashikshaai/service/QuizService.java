package com.sarvashikshaai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.entity.QuizEntity;
import com.sarvashikshaai.model.entity.QuizResultEntity;
import com.sarvashikshaai.repository.QuizRepository;
import com.sarvashikshaai.repository.QuizResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    private final QuizRepository       quizRepo;
    private final QuizResultRepository resultRepo;
    private final OpenAIClient         openAIClient;
    private final ObjectMapper         mapper = new ObjectMapper();

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<QuizEntity> listAll()                     { return quizRepo.findAllByOrderByCreatedAtDesc(); }
    public Optional<QuizEntity> findById(Long id)         { return quizRepo.findById(id); }
    public void delete(Long id)                           { quizRepo.deleteById(id); }

    public QuizEntity save(String title, String subject, String grade, String questionsJson) {
        int count = 0;
        try {
            JsonNode arr = mapper.readTree(questionsJson);
            if (arr.isArray()) count = arr.size();
        } catch (Exception ignored) {}
        return quizRepo.save(new QuizEntity(title, subject, grade, questionsJson, count));
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

    /**
     * Asks OpenAI to generate quiz questions from a topic or extracted text.
     * Returns a JSON string ready to store in questionsJson.
     */
    public String generateQuestionsJson(String topicOrText, int count, String questionTypes) {
        String typeInstruction = buildTypeInstruction(questionTypes.trim(), count);
        String prompt = """
            You are an experienced school teacher in India.
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
            - Language: English unless topic is clearly in Hindi
            - Level: suitable for school students aged 8-16
            """.formatted(count, topicOrText, typeInstruction);

        try {
            String raw = openAIClient.generateCompletion(prompt);
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

    // ── Quiz result submission ────────────────────────────────────────────────

    public record SubmitRequest(Long quizId, String studentName, List<Map<String, String>> answers) {}
    public record SubmitResult(int score, int total, int percent, String answersJson) {}

    public SubmitResult gradeAndSave(SubmitRequest req) throws Exception {
        QuizEntity quiz = quizRepo.findById(req.quizId())
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found: " + req.quizId()));

        JsonNode questions = mapper.readTree(quiz.getQuestionsJson());
        List<Map<String, Object>> annotated = new ArrayList<>();
        int score = 0;

        for (int i = 0; i < questions.size(); i++) {
            JsonNode q       = questions.get(i);
            String  correct  = q.path("answer").asText("").trim();
            String  given    = req.answers().size() > i
                               ? req.answers().get(i).getOrDefault("given", "").trim()
                               : "";
            boolean isRight  = correct.equalsIgnoreCase(given);
            if (isRight) score++;

            annotated.add(Map.of(
                "questionIndex", i,
                "text",          q.path("text").asText(""),
                "given",         given,
                "correct",       correct,
                "isRight",       isRight
            ));
        }

        String answersJson = mapper.writeValueAsString(annotated);
        int percent = questions.size() > 0 ? (score * 100 / questions.size()) : 0;

        QuizResultEntity saved = resultRepo.save(new QuizResultEntity(
            req.quizId(), quiz.getTitle(), req.studentName(),
            score, questions.size(), answersJson
        ));
        log.info("Quiz result saved: quizId={}, student={}, score={}/{}", req.quizId(), req.studentName(), score, questions.size());

        return new SubmitResult(score, questions.size(), percent, answersJson);
    }

    // ── Results ───────────────────────────────────────────────────────────────

    public List<QuizResultEntity> getResultsByQuiz(Long quizId) {
        return resultRepo.findAllByQuizIdOrderByTakenAtDesc(quizId);
    }
    public List<QuizResultEntity> getResultsByStudent(String studentName) {
        return resultRepo.findByStudentNameOrderByTakenAtDesc(studentName);
    }
}

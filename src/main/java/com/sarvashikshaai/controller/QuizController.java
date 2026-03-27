package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.entity.QuizEntity;
import com.sarvashikshaai.service.FileExtractionService;
import com.sarvashikshaai.service.QuizService;
import com.sarvashikshaai.service.StudentListService;
import com.sarvashikshaai.service.UrlContentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/quiz")
@RequiredArgsConstructor
@Slf4j
public class QuizController {

    private static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024L; // 5 MB

    private final QuizService               quizService;
    private final StudentListService        studentListService;
    private final FileExtractionService     extractor;
    private final UrlContentService         urlContentService;
    private final ObjectMapper              objectMapper;
    // ── Teacher dashboard ─────────────────────────────────────────────────────

    @GetMapping("/teacher")
    public String teacherDashboard(Model model) {
        model.addAttribute("quizList",     quizService.listAll());
        model.addAttribute("studentList",  studentListService.getStudents());
        return "quiz/teacher";
    }

    // ── Create quiz ───────────────────────────────────────────────────────────

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createQuiz(
            @RequestParam String topic,
            @RequestParam(defaultValue = "") String grade,
            @RequestParam(defaultValue = "") String description,
            @RequestParam String questionsJson) {
        try {
            QuizEntity saved = quizService.save(topic, grade, description, questionsJson);
            return ResponseEntity.ok(Map.of("id", saved.getId(), "title", saved.getTitle(), "count", saved.getQuestionCount()));
        } catch (Exception e) {
            log.error("Save quiz failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── AI generate questions ─────────────────────────────────────────────────

    @PostMapping("/ai-generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> aiGenerate(
            @RequestParam(required = false, defaultValue = "") String topic,
            @RequestParam(required = false, defaultValue = "") String grade,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false, defaultValue = "") String sourceUrl,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "MCQ") String types,
            @RequestParam(defaultValue = "AUTO") String language,
            @RequestParam(defaultValue = "AUTO") String difficulty,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        try {
            int effectiveCount = Math.max(1, Math.min(count, 15));
            String topicText = topic != null ? topic.trim() : "";
            String gradeText = grade != null ? grade.trim() : "";
            String descText = description != null ? description.trim() : "";
            String resolvedTypes = "MCQ"; // enforce MCQ-only quiz generation
            String sourceExtracted = "";
            StringBuilder contextBuilder = new StringBuilder();
            if (!topicText.isBlank()) contextBuilder.append("Topic: ").append(topicText);
            if (!gradeText.isBlank()) {
                if (!contextBuilder.isEmpty()) contextBuilder.append("\n");
                contextBuilder.append("Grade: ").append(gradeText);
            }
            if (!descText.isBlank()) {
                if (!contextBuilder.isEmpty()) contextBuilder.append("\n");
                contextBuilder.append("Description: ").append(descText);
            }
            String contextText = contextBuilder.toString();

            if (sourceUrl != null && !sourceUrl.isBlank()) {
                String fromUrl = urlContentService.extractContextFromUrl(sourceUrl);
                if (!fromUrl.isBlank()) {
                    sourceExtracted = fromUrl;
                    contextText = contextText.isEmpty() ? fromUrl : contextText + "\n\nSource URL content:\n" + fromUrl;
                }
            }

            if (!sourceExtracted.isBlank() && (topicText.isBlank() || gradeText.isBlank() || descText.isBlank())) {
                QuizService.InferredQuizMeta inferred = quizService.inferQuizMetaFromContext(sourceExtracted);
                if (topicText.isBlank()) topicText = inferred.topic() != null ? inferred.topic().trim() : "";
                if (gradeText.isBlank()) gradeText = inferred.grade() != null ? inferred.grade().trim() : "";
                if (descText.isBlank()) descText = inferred.description() != null ? inferred.description().trim() : "";
            }

            if (file != null && !file.isEmpty()) {
                if (file.getSize() > MAX_UPLOAD_BYTES) {
                    return ResponseEntity.ok(Map.of(
                            "questionsJson", "[]",
                            "error", "File too large. Maximum allowed size is 5 MB."
                    ));
                }
                FileExtractionService.FileType ft = extractor.detectType(file);
                if (ft == FileExtractionService.FileType.PDF) {
                    String pdfText = extractor.extractPdfText(file);
                    contextText = contextText.isEmpty() ? pdfText : contextText + "\n\nFile content:\n" + pdfText;
                } else if (ft == FileExtractionService.FileType.IMAGE) {
                    String imageDataUri = extractor.encodeImageToBase64(file);
                    if (imageDataUri == null || imageDataUri.isBlank()) {
                        return ResponseEntity.ok(Map.of("questionsJson", "[]", "error", "Could not read uploaded image. Try another screenshot."));
                    }
                    QuizService.GeneratedImageQuiz gen = quizService.generateQuestionsFromImage(
                            topicText, gradeText, descText,
                            effectiveCount,
                            resolvedTypes,
                            language,
                            difficulty,
                            imageDataUri
                    );
                    if (gen.error() != null && !gen.error().isBlank()) {
                        return ResponseEntity.ok(Map.of("questionsJson", "[]", "error", gen.error()));
                    }
                    String json = gen.questionsJson() != null ? gen.questionsJson() : "[]";
                    if ("[]".equals(json.trim())) {
                        return ResponseEntity.ok(Map.of("questionsJson", "[]", "error", "Could not generate questions from this screenshot. Try clearer image or add topic hint."));
                    }
                    return ResponseEntity.ok(Map.of(
                            "questionsJson", json,
                            "topic", gen.topic() != null ? gen.topic() : "",
                            "grade", gen.grade() != null ? gen.grade() : "",
                            "description", gen.description() != null ? gen.description() : ""
                    ));
                }
            }

            // If URL was provided but extraction returned empty (transient/network/site-script issues),
            // still proceed using URL hint so first click does not fail.
            if (contextText.isEmpty() && sourceUrl != null && !sourceUrl.isBlank()) {
                contextText = "Generate an educational quiz from this source URL context:\n" + sourceUrl.trim();
            }

            if (contextText.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "questionsJson", "[]",
                        "error", "Please enter topic/grade/description or upload a PDF/image."
                ));
            }
            String json = quizService.generateQuestionsJson(contextText, effectiveCount, resolvedTypes, language, difficulty);
            return ResponseEntity.ok(Map.of(
                    "questionsJson", json != null ? json : "[]",
                    "topic", topicText,
                    "grade", gradeText,
                    "description", descText
            ));
        } catch (Exception e) {
            log.error("AI generate questions failed", e);
            return ResponseEntity.ok(Map.of("questionsJson", "[]", "error", e.getMessage() != null ? e.getMessage() : "Generation failed. Please try again."));
        }
    }

    // ── Delete quiz ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteQuiz(@PathVariable Long id) {
        try {
            quizService.delete(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            log.error("Delete quiz failed for id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Delete failed"));
        }
    }

    // ── Student: take a quiz ─────────────────────────────────────────────────

    @GetMapping("/take/{id}")
    public String takeQuiz(@PathVariable Long id,
                           Model model) {
        return quizService.findById(id).map(quiz -> {
            String questionsJson = "[]";
            try {
                questionsJson = objectMapper.writeValueAsString(quizService.getQuestionsByQuiz(quiz.getId()));
            } catch (Exception e) {
                log.warn("Could not serialize quiz questions for quiz {}: {}", quiz.getId(), e.getMessage());
            }
            model.addAttribute("quiz",        quiz);
            model.addAttribute("quizQuestionsJson", questionsJson);
            model.addAttribute("studentList", studentListService.getStudents());
            return "quiz/take";
        }).orElse("redirect:/quiz/teacher");
    }

    @PostMapping("/{quizId}/lock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lockQuiz(@PathVariable Long quizId) {
        try {
            quizService.lockQuiz(quizId);
            return ResponseEntity.ok(Map.of("status", "locked", "quizId", quizId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/questions/{questionId}/assign")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> assignStudentToQuestion(
            @PathVariable Long questionId,
            @RequestBody QuizService.AssignStudentRequest req
    ) {
        try {
            var saved = quizService.assignStudentToQuestion(questionId, req.studentId());
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "questionId", saved.getQuestionId(),
                    "studentId", saved.getStudentId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/questions/{questionId}/answer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitQuestionAnswer(
            @PathVariable Long questionId,
            @RequestBody QuizService.SubmitAnswerRequest req
    ) {
        try {
            var saved = quizService.submitAnswer(questionId, req.studentId(), req.answer());
            return ResponseEntity.ok(Map.of(
                    "questionId", saved.getQuestionId(),
                    "studentId", saved.getStudentId(),
                    "answer", saved.getAnswer(),
                    "isCorrect", saved.getIsCorrect(),
                    "marksAwarded", saved.getMarksAwarded(),
                    "answeredAt", saved.getAnsweredAt()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{quizId}/question-results")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getQuizQuestionResults(@PathVariable Long quizId) {
        try {
            List<QuizService.QuestionResultRow> results = quizService.getQuizQuestionResults(quizId);
            return ResponseEntity.ok(Map.of("results", results));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

}

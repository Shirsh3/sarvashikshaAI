package com.sarvashikshaai.controller;

import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.entity.QuizEntity;
import com.sarvashikshaai.service.FileExtractionService;
import com.sarvashikshaai.service.QuizService;
import com.sarvashikshaai.service.StudentListService;
import com.sarvashikshaai.service.QuizPdfExportService;
import com.sarvashikshaai.service.UrlContentService;
import com.sarvashikshaai.repository.GradeRefRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.net.URI;
import java.util.ArrayList;

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
    private final GradeRefRepository        gradeRefRepository;
    private final ObjectMapper              objectMapper;
    private final OpenAIClient              openAIClient;
    private final QuizPdfExportService     quizPdfExportService;
    // ── Teacher dashboard ─────────────────────────────────────────────────────

    @GetMapping("/teacher")
    public String teacherDashboard(
            Model model,
            @RequestParam(required = false) String grade,
            Authentication authentication) {
        boolean quizOnlyUser = isQuizOnly(authentication);
        model.addAttribute("quizList", quizService.listAll());
        model.addAttribute("studentList",  studentListService.getStudents());
        model.addAttribute("prefillGrade", grade != null ? grade.trim() : "");
        model.addAttribute("gradeOptions", gradeRefRepository.findAllByOrderBySortOrderAsc());
        model.addAttribute("quizOnlyUser", quizOnlyUser);
        model.addAttribute("ncertUrlEnabled", false);
        if (quizOnlyUser) {
            model.addAttribute("navHomeHref", "/quiz/teacher");
        }
        return "quiz/teacher";
    }

    private static boolean isQuizOnly(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_QUIZ".equals(a.getAuthority()));
    }

    /**
     * Quiz-bank user only: allow MCQ, SHORT, TF, LONG and comma-separated mixes. Teachers stay MCQ-only in controller.
     */
    private static String sanitizeQuizUserTypes(String raw) {
        if (raw == null) {
            return "MCQ";
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (t.isEmpty()) {
            return "MCQ";
        }
        if (t.length() > 80) {
            t = t.substring(0, 80);
        }
        if (!t.matches("^[A-Z0-9,._-]+$")) {
            return "MCQ";
        }
        return t;
    }

    @GetMapping("/{quizId}/results")
    public String quizResultsPage(@PathVariable Long quizId, Model model) {
        QuizEntity quiz = quizService.findById(quizId).orElse(null);
        if (quiz == null) return "redirect:/quiz/teacher";
        model.addAttribute("quiz", quiz);
        model.addAttribute("results", quizService.getQuizQuestionResults(quizId));
        return "quiz-results";
    }

    // ── Create quiz ───────────────────────────────────────────────────────────

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createQuiz(
            @RequestParam String topic,
            @RequestParam(defaultValue = "") String grade,
            @RequestParam(defaultValue = "") String description,
            @RequestParam String questionsJson,
            @RequestParam(defaultValue = "false") String handoutOnly,
            Authentication authentication) {
        try {
            boolean ho = "true".equalsIgnoreCase(handoutOnly) || "1".equals(handoutOnly);
            if (isQuizOnly(authentication)) {
                ho = true;
            }
            QuizEntity saved = quizService.save(topic, grade, description, questionsJson, ho);
            return ResponseEntity.ok(Map.of("id", saved.getId(), "title", saved.getTitle(), "count", saved.getQuestionCount()));
        } catch (Exception e) {
            log.error("Save quiz failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(value = "/{quizId}/export-answers.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportQuizPdfAnswers(@PathVariable Long quizId) {
        return buildPdfResponse(quizId, QuizPdfExportService.ExportMode.ANSWERS, "answers");
    }

    @GetMapping(value = "/{quizId}/export-explanations.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportQuizPdfExplanations(@PathVariable Long quizId) {
        return buildPdfResponse(quizId, QuizPdfExportService.ExportMode.EXPLANATIONS, "explanations");
    }

    // Temporary export for NCERT tab (no DB save)
    @PostMapping(value = "/ncert/export-answers.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportNcertTempAnswersPdf(
            @RequestParam(defaultValue = "NCERT Quiz") String title,
            @RequestParam(defaultValue = "") String description,
            @RequestParam String questionsJson,
            Authentication authentication) {
        if (!isQuizOnly(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] pdf = quizPdfExportService.buildPdfBytesFromQuestionsJson(title, description, questionsJson, QuizPdfExportService.ExportMode.ANSWERS);
        if (pdf == null || pdf.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ncert-quiz-answers.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping(value = "/ncert/export-explanations.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportNcertTempExplanationsPdf(
            @RequestParam(defaultValue = "NCERT Quiz") String title,
            @RequestParam(defaultValue = "") String description,
            @RequestParam String questionsJson,
            Authentication authentication) {
        if (!isQuizOnly(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] pdf = quizPdfExportService.buildPdfBytesFromQuestionsJson(title, description, questionsJson, QuizPdfExportService.ExportMode.EXPLANATIONS);
        if (pdf == null || pdf.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ncert-quiz-explanations.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** @deprecated use {@link #exportQuizPdfAnswers(Long)} */
    @GetMapping(value = "/{quizId}/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportQuizPdfLegacy(@PathVariable Long quizId) {
        return exportQuizPdfAnswers(quizId);
    }

    private ResponseEntity<byte[]> buildPdfResponse(long quizId, QuizPdfExportService.ExportMode mode, String suffix) {
        try {
            byte[] pdf = quizPdfExportService.buildPdfBytes(quizId, mode);
            if (pdf == null || pdf.length == 0) {
                log.warn("PDF export: quizId={} mode={} -> NOT_FOUND (no bytes)", quizId, mode);
                return ResponseEntity.notFound().build();
            }
            log.info("PDF export: quizId={} mode={} bytes={}", quizId, mode, pdf.length);
            return ResponseEntity.status(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quiz-" + quizId + "-" + suffix + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (IllegalStateException e) {
            // Use a clear message instead of disguising as 404
            String msg = e.getMessage() != null ? e.getMessage() : "PDF export not supported.";
            log.warn("PDF export unsupported: quizId={} mode={} msg={}", quizId, mode, msg);
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(msg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("PDF export failed: quizId={} mode={} err={}", quizId, mode, e.getMessage(), e);
            return ResponseEntity.notFound().build();
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
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) {

        try {
            sourceUrl = normalizeNcertHttpToHttps(sourceUrl);
            boolean quizOnly = isQuizOnly(authentication);
            boolean includePerQuestionExplanations = quizOnly;
            String resolvedTypes = quizOnly ? sanitizeQuizUserTypes(types) : "MCQ";

            boolean hasUrl = sourceUrl != null && !sourceUrl.trim().isEmpty();
            boolean hasFile = file != null && !file.isEmpty();
            if (hasUrl && hasFile) {
                return ResponseEntity.ok(Map.of(
                        "questionsJson", "[]",
                        "error", "Choose either upload (screenshot/PDF) or NCERT URL — not both."
                ));
            }

            boolean strictNcertSelectorUrl = isStrictNcertSelectorUrl(sourceUrl);
            int effectiveCount = Math.max(1, Math.min(count, 15));
            String topicText = topic != null ? topic.trim() : "";
            String gradeText = grade != null ? grade.trim() : "";
            String descText = description != null ? description.trim() : "";
            String classifyInput = (topicText + "\n" + gradeText + "\n" + descText).trim();
            String safetyWarning = "";
            if (!strictNcertSelectorUrl && !classifyInput.isBlank()) {
                OpenAIClient.QueryCategory cat = openAIClient.classifyUserQuery(classifyInput);
                if (cat == OpenAIClient.QueryCategory.UNSAFE) {
                    safetyWarning = "Some content was filtered. Generated classroom-safe questions.";
                }
            }
            String sourceExtracted = "";
            boolean hasValidNcertSource = false;
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
                // Requirement: only allow NCERT textbook selector URLs; otherwise error out.
                if (!strictNcertSelectorUrl) {
                    return ResponseEntity.ok(Map.of(
                            "questionsJson", "[]",
                            "error", "https://ncert.nic.in/textbook.php?ihsc1=2-12"
                    ));
                }
                if (strictNcertSelectorUrl) {
                    hasValidNcertSource = true;
                    String urlHint = "NCERT Source URL:\n" + sourceUrl.trim();
                    contextText = contextText.isEmpty() ? urlHint : contextText + "\n\n" + urlHint;
                    String extracted = urlContentService.extractContextFromUrl(sourceUrl.trim());
                    if (extracted != null && !extracted.isBlank()) {
                        contextText = contextText + "\n\n" + extracted;
                        sourceExtracted = extracted;
                    }
                }
            }

            if (!sourceExtracted.isBlank() && (topicText.isBlank() || gradeText.isBlank() || descText.isBlank())) {
                QuizService.InferredQuizMeta inferred = quizService.inferQuizMetaFromContext(sourceExtracted);
                if (topicText.isBlank()) topicText = inferred.topic() != null ? inferred.topic().trim() : "";
                if (gradeText.isBlank()) gradeText = inferred.grade() != null ? inferred.grade().trim() : "";
                if (descText.isBlank()) descText = inferred.description() != null ? inferred.description().trim() : "";
            }

            // Also infer topic/grade from teacher-entered context (including spoken description)
            // so title fields auto-populate even when URL/file source is not used.
            if ((topicText.isBlank() || gradeText.isBlank()) && !contextText.isBlank()) {
                QuizService.InferredQuizMeta inferred = quizService.inferQuizMetaFromContext(contextText);
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
                            includePerQuestionExplanations,
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
            if (topicText.isBlank() && hasValidNcertSource) {
                // For valid NCERT links, do not force teacher to type topic/grade manually.
                topicText = "NCERT source content";
            }
            if (topicText.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "questionsJson", "[]",
                        "error", "Topic is required for strict quiz generation."
                ));
            }
            String json = quizService.generateQuestionsJson(topicText, contextText, effectiveCount, resolvedTypes, language, difficulty, gradeText, includePerQuestionExplanations);
            return ResponseEntity.ok(Map.of(
                    "questionsJson", json != null ? json : "[]",
                    "topic", topicText,
                    "grade", gradeText,
                    "description", descText,
                    "warning", safetyWarning
            ));
        } catch (Exception e) {
            log.error("AI generate questions failed", e);
            return ResponseEntity.ok(Map.of("questionsJson", "[]", "error", e.getMessage() != null ? e.getMessage() : "Generation failed. Please try again."));
        }
    }

    // ── NCERT page-window quiz (ROLE_QUIZ only) ──────────────────────────────

    @PostMapping("/ncert/resolve")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resolveNcertPdf(
            @RequestParam(defaultValue = "") String sourceUrl,
            Authentication authentication) {
        if (!isQuizOnly(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
        }
        String normalized = normalizeNcertHttpToHttps(sourceUrl);
        if (!isStrictNcertSelectorUrl(normalized)) {
            return ResponseEntity.ok(Map.of("error", "Please paste a valid NCERT link like https://ncert.nic.in/textbook.php?leph1=3-8"));
        }
        List<String> pdfLinks = urlContentService.resolvePdfLinksFromUrl(normalized);
        if (pdfLinks.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "Could not resolve any NCERT PDF from this link. Try another chapter link."));
        }
        String resolvedTitle = urlContentService.resolveNcertPageTitle(normalized);
        String pdfUrl = pdfLinks.get(0);
        byte[] pdfBytes = urlContentService.fetchPdfBytes(pdfUrl);
        int totalPages = extractor.countPdfPages(pdfBytes);
        if (totalPages <= 0) {
            return ResponseEntity.ok(Map.of(
                    "pdfUrl", pdfUrl,
                    "totalPages", 0,
                    "title", resolvedTitle,
                    "warning", "Could not read PDF page count (download/extraction issue). You can still try generating."
            ));
        }
        return ResponseEntity.ok(Map.of(
                "pdfUrl", pdfUrl,
                "totalPages", totalPages,
                "title", resolvedTitle
        ));
    }

    @PostMapping("/ncert/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateNcertPageWindow(
            @RequestParam(defaultValue = "") String sourceUrl,
            @RequestParam(defaultValue = "") String pdfUrl,
            @RequestParam(defaultValue = "1") int startPage,
            @RequestParam(defaultValue = "5") int pageWindow,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "MCQ") String types,
            @RequestParam(defaultValue = "AUTO") String language,
            @RequestParam(defaultValue = "AUTO") String difficulty,
            Authentication authentication) {
        if (!isQuizOnly(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
        }
        String normalized = normalizeNcertHttpToHttps(sourceUrl);
        if (!isStrictNcertSelectorUrl(normalized)) {
            return ResponseEntity.ok(Map.of("error", "Invalid NCERT link."));
        }
        if (pdfUrl == null || pdfUrl.isBlank()) {
            return ResponseEntity.ok(Map.of("error", "PDF not resolved. Click Load first."));
        }
        int window = Math.max(1, Math.min(pageWindow, 10));
        int effectiveCount = Math.max(1, Math.min(count, 10));
        int s = Math.max(1, startPage);
        int e = s + window - 1;
        byte[] pdfBytes = urlContentService.fetchPdfBytes(pdfUrl.trim());
        if (pdfBytes.length == 0) {
            return ResponseEntity.ok(Map.of("error", "Could not download the NCERT PDF. Please try again."));
        }
        log.info("NCERT generate: pdfBytes={}KB url={}", Math.max(1, pdfBytes.length / 1024), pdfUrl.trim());
        int total = extractor.countPdfPages(pdfBytes);
        if (total > 0 && s > total) {
            return ResponseEntity.ok(Map.of("error", "Start page is beyond the end of the PDF.", "totalPages", total));
        }
        String pageText = extractor.extractPdfTextPages(pdfBytes, s, e, 20000);
        if (pageText == null || pageText.isBlank()) {
            return ResponseEntity.ok(Map.of("error", "No readable text found in these pages. Try a different page range."));
        }
        log.info("NCERT generate: pages {}-{} extractedChars={}", s, e, pageText.length());
        String context = "NCERT Source URL:\n" + normalized + "\n\n"
                + "NCERT PDF:\n" + pdfUrl.trim() + "\n"
                + "Pages: " + s + "-" + e + "\n\n"
                + pageText;
        String questionsJson = quizService.generateQuestionsJsonFromContentOnly(
                context,
                effectiveCount,
                types,
                language,
                difficulty,
                true
        );
        log.info("NCERT generate: questionsJsonLen={}", questionsJson == null ? 0 : questionsJson.length());
        String inferredTopic = "";
        try {
            QuizService.InferredQuizMeta meta = quizService.inferQuizMetaFromContext(pageText);
            inferredTopic = meta != null ? meta.topic() : "";
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "questionsJson", questionsJson != null ? questionsJson : "[]",
                "startPage", s,
                "endPage", e,
                "totalPages", total,
                "topic", inferredTopic != null ? inferredTopic : ""
        ));
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
            log.error("Get quiz results failed for quizId={}: {}", quizId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Could not load quiz results. Please refresh and try again."));
        }
    }

    /**
     * Allow only NCERT textbook selector URLs, e.g. {@code https://ncert.nic.in/textbook.php?leph1=2-8}.
     */
    /** Browsers sometimes copy http:// — our allowlist requires https. */
    private static String normalizeNcertHttpToHttps(String sourceUrl) {
        if (sourceUrl == null) {
            return "";
        }
        String t = sourceUrl.trim();
        if (t.isEmpty()) {
            return t;
        }
        if (t.length() > 7 && t.regionMatches(true, 0, "http://", 0, 7) && t.toLowerCase(Locale.ROOT).contains("ncert.nic.in")) {
            return "https" + t.substring(4);
        }
        return t;
    }

    private boolean isStrictNcertSelectorUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        String trimmed = sourceUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
            if (uri.getHost() == null || !"ncert.nic.in".equalsIgnoreCase(uri.getHost())) return false;
            if (uri.getPath() == null || !"/textbook.php".equals(uri.getPath())) return false;
            String q = uri.getQuery();
            return q != null && !q.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

}

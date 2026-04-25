package com.sarvashikshaai.service;

import com.sarvashikshaai.model.entity.QuizEntity;
import com.sarvashikshaai.model.entity.QuizQuestionEntity;
import com.sarvashikshaai.repository.QuizQuestionRepository;
import com.sarvashikshaai.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

/**
 * Printable handout PDFs: student-oriented with answer key, or teacher copy with teaching notes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizPdfExportService {

    public enum ExportMode {
        /** Questions + short answer key at the end. */
        ANSWERS,
        /** Each question with correct answer and stored explanation (for quiz-bank / AI). */
        EXPLANATIONS
    }

    private static final float MARGIN = 50f;
    private static final float LEADING = 14f;
    private static final float TITLE_SIZE = 16f;
    private static final float BODY_SIZE = 11f;
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float MAX_TEXT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    /**
     * Production-safe font loading:
     * - Fonts are bundled under src/main/resources/fonts/
     * - Loaded from classpath and embedded into the PDF (no OS font dependency).
     *
     * Required resource:
     * - /fonts/NotoSans-Regular.ttf
     *
     * Optional:
     * - /fonts/NotoSansDevanagari-Regular.ttf (if you prefer explicit Devanagari font)
     */
    // Keep paths stable for Docker/EC2: load from classpath resources.
    // Your repo currently stores the Noto families under:
    //   src/main/resources/fonts/Noto_Sans,Noto_Sans_Devanagari/Noto_Sans/static/...
    // So we point to that concrete location.
    private static final String FONT_NOTO_SANS =
            "/fonts/Noto_Sans,Noto_Sans_Devanagari/Noto_Sans/static/NotoSans-Regular.ttf";
    private static final String FONT_NOTO_SANS_DEVANAGARI =
            "/fonts/Noto_Sans,Noto_Sans_Devanagari/Noto_Sans_Devanagari/static/NotoSansDevanagari-Regular.ttf";

    private static boolean containsDevanagari(String s) {
        if (s == null || s.isBlank()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0900' && c <= '\u097F') return true;
        }
        return false;
    }

    private static PDFont loadEmbeddedFont(PDDocument doc, String classpathResource) throws IOException {
        try (InputStream in = QuizPdfExportService.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException(
                        "PDF font missing: add " + classpathResource + " under src/main/resources/fonts/ and restart the server."
                );
            }
            // Embed font subset into the PDF
            return PDType0Font.load(doc, in, true);
        }
    }

    private static FontBundle chooseFonts(PDDocument doc, String contentHint) throws IOException {
        // Prefer a single universal font to avoid mixed-font rendering issues.
        // If you add NotoSansDevanagari-Regular.ttf and want explicit Devanagari for those scripts,
        // uncomment the conditional branch below.
        //
        // if (containsDevanagari(contentHint)) {
        //     PDFont devanagari = loadEmbeddedFont(doc, FONT_NOTO_SANS_DEVANAGARI);
        //     return new FontBundle(devanagari, devanagari);
        // }
        PDFont noto = loadEmbeddedFont(doc, FONT_NOTO_SANS);
        return new FontBundle(noto, noto);
    }

    private record FontBundle(PDFont body, PDFont title) {}

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public byte[] buildPdfBytes(long quizId) {
        return buildPdfBytes(quizId, ExportMode.ANSWERS);
    }

    public byte[] buildPdfBytes(long quizId, ExportMode mode) {
        Optional<QuizEntity> opt = quizRepository.findById(quizId);
        if (opt.isEmpty()) {
            log.warn("PDF build: quizId={} mode={} -> quiz not found", quizId, mode);
            return null;
        }
        List<QuizQuestionEntity> questions = questionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId);
        log.info("PDF build: quizId={} mode={} questions={}", quizId, mode, questions != null ? questions.size() : 0);
        if (mode == ExportMode.EXPLANATIONS) {
            return renderExplanations(opt.get(), questions);
        }
        return renderWithAnswerKey(opt.get(), questions);
    }

    /** Temporary export for NCERT tab: render directly from questions JSON (no DB). */
    public byte[] buildPdfBytesFromQuestionsJson(String title, String description, String questionsJson, ExportMode mode) {
        List<TempQuestion> qs = parseTempQuestions(questionsJson);
        if (qs.isEmpty()) return null;
        TempQuiz tq = new TempQuiz(n(title), n(description));
        if (mode == ExportMode.EXPLANATIONS) {
            return renderExplanationsTemp(tq, qs);
        }
        return renderWithAnswerKeyTemp(tq, qs);
    }

    private record TempQuiz(String title, String description) {}
    private record TempQuestion(String type, String text, String a, String b, String c, String d, String answer, String explanation) {}

    private static List<TempQuestion> parseTempQuestions(String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) return List.of();
        try {
            JsonNode root = MAPPER.readTree(questionsJson);
            if (root == null || !root.isArray()) return List.of();
            List<TempQuestion> out = new ArrayList<>();
            for (JsonNode n : root) {
                if (n == null || !n.isObject()) continue;
                String type = n.path("type").asText("MCQ");
                String text = n.path("text").asText("");
                String ans = n.path("answer").asText("");
                String expl = n.path("explanation").asText("");
                String oa = "", ob = "", oc = "", od = "";
                JsonNode opts = n.path("options");
                if (opts != null && opts.isArray()) {
                    oa = opts.size() > 0 ? opts.get(0).asText("") : "";
                    ob = opts.size() > 1 ? opts.get(1).asText("") : "";
                    oc = opts.size() > 2 ? opts.get(2).asText("") : "";
                    od = opts.size() > 3 ? opts.get(3).asText("") : "";
                }
                out.add(new TempQuestion(type, text, oa, ob, oc, od, ans, expl));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private byte[] renderWithAnswerKeyTemp(TempQuiz quiz, List<TempQuestion> questions) {
        try (PDDocument doc = new PDDocument()) {
            String hint = quiz.title() + " " + quiz.description();
            FontBundle fonts = chooseFonts(doc, hint);
            PageContext ctx = new PageContext(doc, fonts);
            ctx.newPage();
            float y = ctx.getYStart();
            y = writeWrapped(ctx, ctx.fontTitle, TITLE_SIZE, y, n(quiz.title()), MAX_TEXT_WIDTH, LEADING + 2);
            y -= LEADING;
            if (!n(quiz.description()).isBlank()) {
                y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, n(quiz.description()), MAX_TEXT_WIDTH, LEADING);
                y -= LEADING;
            }
            y -= LEADING * 0.5f;

            int qNum = 0;
            for (TempQuestion q : questions) {
                qNum++;
                y = drawQuestionStudentTemp(ctx, y, qNum, q);
            }

            y -= LEADING;
            y = writeWrapped(ctx, ctx.fontTitle, TITLE_SIZE, y, "Answer key", MAX_TEXT_WIDTH, LEADING + 2);
            y -= LEADING * 0.5f;
            for (int i = 0; i < questions.size(); i++) {
                TempQuestion q = questions.get(i);
                String line = (i + 1) + ". " + n(q.answer());
                y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, line, MAX_TEXT_WIDTH, LEADING);
            }
            return saveDoc(doc);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build PDF: " + e.getMessage(), e);
        }
    }

    private byte[] renderExplanationsTemp(TempQuiz quiz, List<TempQuestion> questions) {
        try (PDDocument doc = new PDDocument()) {
            String hint = quiz.title() + " " + quiz.description();
            FontBundle fonts = chooseFonts(doc, hint);
            PageContext ctx = new PageContext(doc, fonts);
            ctx.newPage();
            float y = ctx.getYStart();
            y = writeWrapped(ctx, ctx.fontTitle, TITLE_SIZE, y, n(quiz.title()) + " — teaching copy", MAX_TEXT_WIDTH, LEADING + 2);
            y -= LEADING;
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "Answers and teaching notes (for each question).", MAX_TEXT_WIDTH, LEADING);
            y -= LEADING * 1.2f;

            int qNum = 0;
            for (TempQuestion q : questions) {
                qNum++;
                y = drawQuestionTeacherBlockTemp(ctx, y, qNum, q);
            }
            return saveDoc(doc);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build PDF: " + e.getMessage(), e);
        }
    }

    private float drawQuestionStudentTemp(PageContext ctx, float y, int num, TempQuestion q) throws IOException {
        y = ctx.ensureSpace(y, 100f);
        String head = "Q" + num + " (" + n(q.type()) + ")";
        y = writeWrapped(ctx, ctx.fontTitle, BODY_SIZE, y, head, MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.3f;
        y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, n(q.text()), MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.2f;
        if ("MCQ".equalsIgnoreCase(n(q.type()))) {
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "A) " + n(q.a()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "B) " + n(q.b()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "C) " + n(q.c()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "D) " + n(q.d()), MAX_TEXT_WIDTH, LEADING);
        } else {
            y -= LEADING * 0.3f;
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "Answer: ________________________________", MAX_TEXT_WIDTH, LEADING);
        }
        y -= LEADING;
        return y;
    }

    private float drawQuestionTeacherBlockTemp(PageContext ctx, float y, int num, TempQuestion q) throws IOException {
        y = ctx.ensureSpace(y, 140f);
        String head = "Q" + num + " (" + n(q.type()) + ")";
        y = writeWrapped(ctx, ctx.fontTitle, BODY_SIZE, y, head, MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.25f;
        y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, n(q.text()), MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.2f;
        if ("MCQ".equalsIgnoreCase(n(q.type()))) {
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "A) " + n(q.a()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "B) " + n(q.b()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "C) " + n(q.c()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "D) " + n(q.d()), MAX_TEXT_WIDTH, LEADING);
        }
        y -= LEADING * 0.3f;
        y = writeWrapped(ctx, ctx.fontTitle, BODY_SIZE, y, "Correct answer: " + n(q.answer()), MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.25f;
        String expl = n(q.explanation());
        if (expl.isBlank()) {
            expl = "—";
        }
        y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "Teaching note: " + expl, MAX_TEXT_WIDTH, LEADING);
        y -= LEADING;
        return y;
    }

    private byte[] renderWithAnswerKey(QuizEntity quiz, List<QuizQuestionEntity> questions) {
        try (PDDocument doc = new PDDocument()) {
            String hint = (quiz.getTitle() + " " + quiz.getDescription());
            // Include some question text in hint to detect Sanskrit/Hindi even if title is English.
            if (questions != null) {
                int cap = Math.min(questions.size(), 2);
                for (int i = 0; i < cap; i++) {
                    QuizQuestionEntity q = questions.get(i);
                    if (q == null) continue;
                    hint += " " + n(q.getQuestionText());
                    hint += " " + n(q.getOptionA()) + " " + n(q.getOptionB()) + " " + n(q.getOptionC()) + " " + n(q.getOptionD());
                    hint += " " + n(q.getCorrectAnswer()) + " " + n(q.getAnswerExplanation());
                }
            }
            FontBundle fonts = chooseFonts(doc, hint);
            PageContext ctx = new PageContext(doc, fonts);
            ctx.newPage();
            float y = ctx.getYStart();
            y = writeWrapped(ctx, ctx.fontTitle, TITLE_SIZE, y, n(quiz.getTitle()), MAX_TEXT_WIDTH, LEADING + 2);
            y -= LEADING;
            if (!n(quiz.getDescription()).isBlank()) {
                y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, n(quiz.getDescription()), MAX_TEXT_WIDTH, LEADING);
                y -= LEADING;
            }
            y -= LEADING * 0.5f;

            int qNum = 0;
            for (QuizQuestionEntity q : questions) {
                qNum++;
                y = drawQuestionStudent(ctx, y, qNum, q);
            }

            y -= LEADING;
            y = writeWrapped(ctx, ctx.fontTitle, TITLE_SIZE, y, "Answer key", MAX_TEXT_WIDTH, LEADING + 2);
            y -= LEADING * 0.5f;
            for (int i = 0; i < questions.size(); i++) {
                QuizQuestionEntity q = questions.get(i);
                String line = (i + 1) + ". " + n(q.getCorrectAnswer());
                y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, line, MAX_TEXT_WIDTH, LEADING);
            }

            return saveDoc(doc);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build PDF: " + e.getMessage(), e);
        }
    }

    private byte[] renderExplanations(QuizEntity quiz, List<QuizQuestionEntity> questions) {
        try (PDDocument doc = new PDDocument()) {
            String hint = (quiz.getTitle() + " " + quiz.getDescription());
            if (questions != null) {
                int cap = Math.min(questions.size(), 2);
                for (int i = 0; i < cap; i++) {
                    QuizQuestionEntity q = questions.get(i);
                    if (q == null) continue;
                    hint += " " + n(q.getQuestionText());
                    hint += " " + n(q.getOptionA()) + " " + n(q.getOptionB()) + " " + n(q.getOptionC()) + " " + n(q.getOptionD());
                    hint += " " + n(q.getCorrectAnswer()) + " " + n(q.getAnswerExplanation());
                }
            }
            FontBundle fonts = chooseFonts(doc, hint);
            PageContext ctx = new PageContext(doc, fonts);
            ctx.newPage();
            float y = ctx.getYStart();
            y = writeWrapped(ctx, ctx.fontTitle, TITLE_SIZE, y, n(quiz.getTitle()) + " — teaching copy", MAX_TEXT_WIDTH, LEADING + 2);
            y -= LEADING;
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "Answers and teaching notes (for each question).", MAX_TEXT_WIDTH, LEADING);
            y -= LEADING * 1.2f;

            int qNum = 0;
            for (QuizQuestionEntity q : questions) {
                qNum++;
                y = drawQuestionTeacherBlock(ctx, y, qNum, q);
            }
            return saveDoc(doc);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build PDF: " + e.getMessage(), e);
        }
    }

    private static byte[] saveDoc(PDDocument doc) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.save(bos);
        return bos.toByteArray();
    }

    private float drawQuestionStudent(PageContext ctx, float y, int num, QuizQuestionEntity q) throws IOException {
        y = ctx.ensureSpace(y, 100f);
        String head = "Q" + num + " (" + n(q.getQuestionType()) + ")";
        y = writeWrapped(ctx, ctx.fontTitle, BODY_SIZE, y, head, MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.3f;
        y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, n(q.getQuestionText()), MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.2f;
        if ("MCQ".equalsIgnoreCase(n(q.getQuestionType()))) {
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "A) " + n(q.getOptionA()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "B) " + n(q.getOptionB()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "C) " + n(q.getOptionC()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "D) " + n(q.getOptionD()), MAX_TEXT_WIDTH, LEADING);
        } else {
            y -= LEADING * 0.3f;
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "Answer: ________________________________", MAX_TEXT_WIDTH, LEADING);
        }
        y -= LEADING;
        return y;
    }

    private float drawQuestionTeacherBlock(PageContext ctx, float y, int num, QuizQuestionEntity q) throws IOException {
        y = ctx.ensureSpace(y, 140f);
        String head = "Q" + num + " (" + n(q.getQuestionType()) + ")";
        y = writeWrapped(ctx, ctx.fontTitle, BODY_SIZE, y, head, MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.25f;
        y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, n(q.getQuestionText()), MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.2f;
        if ("MCQ".equalsIgnoreCase(n(q.getQuestionType()))) {
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "A) " + n(q.getOptionA()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "B) " + n(q.getOptionB()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "C) " + n(q.getOptionC()), MAX_TEXT_WIDTH, LEADING);
            y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "D) " + n(q.getOptionD()), MAX_TEXT_WIDTH, LEADING);
        }
        y -= LEADING * 0.3f;
        y = writeWrapped(ctx, ctx.fontTitle, BODY_SIZE, y, "Correct answer: " + n(q.getCorrectAnswer()), MAX_TEXT_WIDTH, LEADING);
        y -= LEADING * 0.25f;
        String expl = n(q.getAnswerExplanation());
        if (expl.isBlank()) {
            expl = "— (No teaching note in database; use quiz-bank generation to include AI notes.)";
        }
        y = writeWrapped(ctx, ctx.font, BODY_SIZE, y, "Teaching note: " + expl, MAX_TEXT_WIDTH, LEADING);
        y -= LEADING;
        return y;
    }

    // NOTE: intentionally no filesystem/OS font fallback.

    private static float writeWrapped(
            PageContext ctx, PDFont font, float size, float y, String text,
            float maxW, float leading) throws IOException {
        List<String> lines = wrap(text, font, size, maxW);
        for (String line : lines) {
            y = ctx.ensureSpace(y, leading + 2);
            try (PDPageContentStream cs = new PDPageContentStream(ctx.doc, ctx.currentPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setFont(font, size);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, y);
                cs.showText(line);
                cs.endText();
            }
            y -= leading;
        }
        return y;
    }

    private static List<String> wrap(String text, PDFont font, float size, float maxW) {
        String t = n(text).replace("\r", "").replace('\n', ' ').trim();
        if (t.isEmpty()) {
            return List.of("");
        }
        List<String> out = new ArrayList<>();
        String[] words = t.split("\\s+");
        StringBuilder line = new StringBuilder();
        try {
            for (String w : words) {
                if (w.isEmpty()) continue;

                // If the current line is empty and the word itself doesn't fit,
                // break the word at character boundaries (works for Unicode too).
                if (line.isEmpty()) {
                    if (stringWidth(font, size, w) <= maxW) {
                        line.append(w);
                        continue;
                    }
                    out.addAll(breakLongToken(font, size, w, maxW));
                    continue;
                }

                String trial = line + " " + w;
                if (stringWidth(font, size, trial) <= maxW) {
                    line.append(" ").append(w);
                } else {
                    out.add(line.toString());
                    line = new StringBuilder();
                    if (stringWidth(font, size, w) <= maxW) {
                        line.append(w);
                    } else {
                        out.addAll(breakLongToken(font, size, w, maxW));
                    }
                }
            }
        } catch (IOException e) {
            return List.of(t);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private static float stringWidth(PDFont font, float size, String s) throws IOException {
        return font.getStringWidth(s) / 1000f * size;
    }

    private static List<String> breakLongToken(PDFont font, float size, String token, float maxW) throws IOException {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            cur.append(token.charAt(i));
            if (stringWidth(font, size, cur.toString()) > maxW && cur.length() > 1) {
                // emit everything except last char
                String emit = cur.substring(0, cur.length() - 1);
                parts.add(emit);
                cur = new StringBuilder();
                cur.append(token.charAt(i));
            }
        }
        if (!cur.isEmpty()) parts.add(cur.toString());
        return parts;
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }

    private static final class PageContext {
        final PDDocument doc;
        final PDFont fontTitle;
        final PDFont font;
        PDPage currentPage;

        PageContext(PDDocument doc, FontBundle fonts) {
            this.doc = doc;
            this.fontTitle = fonts.title();
            this.font = fonts.body();
        }

        void newPage() {
            currentPage = new PDPage(PDRectangle.LETTER);
            doc.addPage(currentPage);
        }

        float getYStart() {
            return PAGE_HEIGHT - MARGIN;
        }

        float ensureSpace(float y, float minFromBottom) throws IOException {
            if (y < MARGIN + minFromBottom) {
                newPage();
                return PAGE_HEIGHT - MARGIN;
            }
            return y;
        }
    }
}

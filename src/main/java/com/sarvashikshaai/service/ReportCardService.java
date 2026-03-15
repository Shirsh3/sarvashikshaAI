package com.sarvashikshaai.service;

import com.sarvashikshaai.ai.OpenAIClient;
import com.sarvashikshaai.model.entity.StudentReport;
import com.sarvashikshaai.repository.AttendanceRepository;
import com.sarvashikshaai.repository.QuizResultRepository;
import com.sarvashikshaai.repository.ReadingFeedbackRepository;
import com.sarvashikshaai.repository.StudentReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates student report cards.
 *
 * Flow:
 *   1. Aggregate all relevant H2 data for the student + term
 *   2. If a cached report already exists → return it (zero AI cost)
 *   3. Otherwise → build a rich prompt from aggregated data → ONE OpenAI call → save → return
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportCardService {

    private final AttendanceRepository      attendanceRepo;
    private final ReadingFeedbackRepository readingRepo;
    private final QuizResultRepository      quizResultRepo;
    private final StudentReportRepository   reportRepo;
    private final StudentSyncService        syncService;
    private final OpenAIClient              openAIClient;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns a cached report if it exists. */
    public Optional<StudentReport> getCached(String studentName, String termLabel) {
        return reportRepo.findByStudentNameAndTermLabel(studentName, termLabel);
    }

    /** Lists all reports ever generated for a student. */
    public List<StudentReport> getHistory(String studentName) {
        return reportRepo.findByStudentNameOrderByGeneratedAtDesc(studentName);
    }

    /** Deletes a cached report so it can be regenerated. */
    public void invalidate(String studentName, String termLabel) {
        reportRepo.findByStudentNameAndTermLabel(studentName, termLabel)
                  .ifPresent(reportRepo::delete);
    }

    /**
     * Main entry point — generates (or returns cached) report.
     * fromDate/toDate define the term window.
     */
    public StudentReport generateOrLoad(String studentName, String termLabel,
                                         LocalDate fromDate, LocalDate toDate) {
        // Return cached if available
        Optional<StudentReport> cached = getCached(studentName, termLabel);
        if (cached.isPresent()) {
            log.info("Returning cached report for {} / {}", studentName, termLabel);
            return cached.get();
        }

        // Aggregate data from H2
        AggregatedData data = aggregate(studentName, fromDate, toDate);

        // Build prompt and call OpenAI
        String prompt = buildPrompt(studentName, termLabel, fromDate, toDate, data);
        String reportText;
        try {
            reportText = openAIClient.generateCompletion(prompt);
        } catch (Exception e) {
            log.error("Report card AI call failed for {}: {}", studentName, e.getMessage());
            reportText = buildFallbackReport(studentName, termLabel, data);
        }

        // Save to H2 (next view is free)
        StudentReport report = new StudentReport(
            studentName, termLabel,
            data.attendancePercent, data.avgFluency, data.avgAccuracy,
            data.avgQuizScore, data.readingSessions, data.topDifficultWords,
            reportText
        );
        reportRepo.findByStudentNameAndTermLabel(studentName, termLabel)
                  .ifPresent(reportRepo::delete); // replace if somehow a race condition occurred
        return reportRepo.save(report);
    }

    // ── Data aggregation ──────────────────────────────────────────────────────

    private record AggregatedData(
        int attendancePercent, int presentDays, int totalMarkedDays,
        double avgFluency, double avgAccuracy, double avgPronunciation, double avgConfidence,
        int readingSessions,
        double avgQuizScore, int quizzesTaken,
        String topDifficultWords,
        String readingTrend   // "improving", "stable", "needs_attention"
    ) {}

    private AggregatedData aggregate(String studentName, LocalDate from, LocalDate to) {
        // Attendance
        var attRecords = attendanceRepo.findByDateBetweenOrderByDateAscStudentNameAsc(from, to)
                .stream().filter(r -> r.getStudentName().equalsIgnoreCase(studentName)).toList();
        int totalMarked = attRecords.size();
        int present     = (int) attRecords.stream().filter(r -> r.isPresent()).count();
        int attPct      = totalMarked > 0 ? (present * 100 / totalMarked) : 0;

        // Reading feedback
        var readings = readingRepo.findByStudentNameOrderByCreatedAtDesc(studentName).stream()
                .filter(r -> !r.getDate().isBefore(from) && !r.getDate().isAfter(to))
                .toList();
        int sessions       = readings.size();
        double avgFluency  = sessions > 0 ? readings.stream().mapToInt(r -> r.getFluencyScore() != null ? r.getFluencyScore() : 0).average().orElse(0) : 0;
        double avgAccuracy = sessions > 0 ? readings.stream().mapToInt(r -> r.getAccuracyScore() != null ? r.getAccuracyScore() : 0).average().orElse(0) : 0;
        double avgPronun   = sessions > 0 ? readings.stream().mapToInt(r -> r.getPronunciationScore() != null ? r.getPronunciationScore() : 0).average().orElse(0) : 0;
        double avgConf     = sessions > 0 ? readings.stream().mapToInt(r -> r.getConfidenceScore() != null ? r.getConfidenceScore() : 0).average().orElse(0) : 0;

        // Reading trend: compare first half vs second half fluency
        String trend = "stable";
        if (sessions >= 4) {
            double firstHalf  = readings.subList(sessions / 2, sessions).stream().mapToInt(r -> r.getFluencyScore() != null ? r.getFluencyScore() : 0).average().orElse(0);
            double secondHalf = readings.subList(0, sessions / 2).stream().mapToInt(r -> r.getFluencyScore() != null ? r.getFluencyScore() : 0).average().orElse(0);
            trend = secondHalf > firstHalf + 0.5 ? "improving" : secondHalf < firstHalf - 0.5 ? "needs_attention" : "stable";
        }

        // Top difficult words
        String difficultWords = readings.stream()
                .map(r -> r.getDifficultWords() == null ? "" : r.getDifficultWords())
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim).filter(s -> !s.isBlank())
                .collect(Collectors.groupingBy(w -> w.toLowerCase(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));

        // Quiz results
        var quizResults = quizResultRepo.findByStudentNameOrderByTakenAtDesc(studentName).stream()
                .filter(r -> r.getTakenAt() != null &&
                        !r.getTakenAt().isBefore(from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)) &&
                        !r.getTakenAt().isAfter(to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)))
                .toList();
        int quizzesTaken  = quizResults.size();
        double avgQuizPct = quizzesTaken > 0
                ? quizResults.stream().mapToDouble(r -> r.getTotalQuestions() > 0 ? 100.0 * r.getScore() / r.getTotalQuestions() : 0).average().orElse(0)
                : 0;

        return new AggregatedData(attPct, present, totalMarked, avgFluency, avgAccuracy,
                avgPronun, avgConf, sessions, avgQuizPct, quizzesTaken, difficultWords, trend);
    }

    // ── Prompt builder ────────────────────────────────────────────────────────

    private String buildPrompt(String name, String term, LocalDate from, LocalDate to, AggregatedData d) {
        return """
            You are a compassionate and professional school teacher writing a term report card for an NGO school in India.
            Write a report card for this student. Include BOTH a Hindi section and an English section.
            Do not use or include any student or personal names in the report; refer to the student as "this student" or similar.

            Term: %s (%s to %s)

            DATA FROM THE TERM:
            - Attendance: %d%% (%d present out of %d marked school days)
            - Reading Practice Sessions: %d
            - Average Fluency Score: %.1f/10
            - Average Accuracy Score: %.1f/10
            - Average Pronunciation Score: %.1f/10
            - Average Confidence Score: %.1f/10
            - Reading Progress Trend: %s
            - Most Difficult Words: %s
            - Quizzes Taken: %d, Average Quiz Score: %.0f%%

            Write the report card in this exact structure:
            === HINDI REPORT ===
            [2-3 paragraphs in Hindi: performance summary, reading progress, attendance, areas to improve, encouraging closing. No personal names.]

            === ENGLISH REPORT ===
            [Same content in English, 2-3 paragraphs. No personal names.]

            === STRENGTHS ===
            [Bullet list of 3 strengths]

            === AREAS TO IMPROVE ===
            [Bullet list of 3 specific, actionable improvement areas]

            === TEACHER'S NOTE ===
            [One warm, personalised closing sentence. Do not use any personal name; say "this student" or "you" if needed.]
            """.formatted(
                term,
                from.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                to.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                d.attendancePercent(), d.presentDays(), d.totalMarkedDays(),
                d.readingSessions(),
                d.avgFluency(), d.avgAccuracy(), d.avgPronunciation(), d.avgConfidence(),
                d.readingTrend().replace("_", " "),
                d.topDifficultWords().isBlank() ? "none recorded" : d.topDifficultWords(),
                d.quizzesTaken(), d.avgQuizScore()
        );
    }

    private String buildFallbackReport(String name, String term, AggregatedData d) {
        return String.format("""
            === HINDI REPORT ===
            इस सत्र में प्रदर्शन देखा गया। उपस्थिति: %d%%。 पढ़ाई में %d सत्र पूरे किए।

            === ENGLISH REPORT ===
            This student has completed %d reading sessions this term with an attendance of %d%%.
            Average fluency score: %.1f/10. Keep up the effort!

            === STRENGTHS ===
            - Regular participation
            - Effort in reading practice

            === AREAS TO IMPROVE ===
            - Continue reading practice daily
            - Work on pronunciation of difficult words: %s

            === TEACHER'S NOTE ===
            Keep working hard — you are making progress!
            """, d.attendancePercent(), d.readingSessions(),
                 d.readingSessions(), d.attendancePercent(), d.avgFluency(),
                 d.topDifficultWords().isBlank() ? "focus on clarity" : d.topDifficultWords());
    }
}

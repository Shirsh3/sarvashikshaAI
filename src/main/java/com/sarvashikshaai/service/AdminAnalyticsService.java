package com.sarvashikshaai.service;

import com.sarvashikshaai.model.dto.StudentActivity;
import com.sarvashikshaai.model.dto.ReadingLevel;
import com.sarvashikshaai.model.dto.StudentMetrics;
import com.sarvashikshaai.model.dto.StreakResponse;
import com.sarvashikshaai.model.entity.QuestionResponseEntity;
import com.sarvashikshaai.model.entity.ReadingFeedbackRecord;
import com.sarvashikshaai.model.entity.StudentEntity;
import com.sarvashikshaai.repository.AttendanceRepository;
import com.sarvashikshaai.repository.QuestionResponseRepository;
import com.sarvashikshaai.repository.ReadingFeedbackRepository;
import com.sarvashikshaai.repository.StudentEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("MMM d HH:mm")
            .withZone(ZoneId.systemDefault());

    private final QuestionResponseRepository responseRepo;
    private final ReadingFeedbackRepository readingFeedbackRepo;
    private final AttendanceRepository attendanceRepo;
    private final StudentEntityRepository studentRepo;
    private final TeacherInsightService teacherInsightService;
    private final StreakService streakService;

    @Transactional(readOnly = true)
    public Map<String, Object> buildStudentAnalytics(String studentCode) {
        Map<String, Object> out = new LinkedHashMap<>();
        StudentEntity student = studentRepo.findByCode(studentCode);
        Optional<StudentEntity> st = Optional.ofNullable(student);
        String name = st.map(StudentEntity::getName).orElse(studentCode);

        // Do not let inactive students contribute anywhere.
        if (student == null || !student.isActive()) {
            out.put("error", "Student is inactive.");
            out.put("studentCode", studentCode);
            out.put("studentName", name);
            out.put("chartLabels", List.of());
            out.put("chartScores", List.of());
            out.put("chartKinds", List.of());
            out.put("scoreHistory", List.of());
            out.put("subjectMastery", List.of());
            out.put("attendancePct", 0);
            out.put("totalResponses", 0);
            out.put("correctCount", 0);
            out.put("readingSessions", 0);
            out.put("teacherNoteEn", "");
            out.put("teacherNoteHi", "");
            out.put("teacherNote", "");
            out.put("teacherNoteFallback", "");
            out.put("streakCurrent", 0);
            out.put("streakLevel", "NONE");
            out.put("streakMessage", "");
            return out;
        }

        List<QuestionResponseEntity> quizRows = responseRepo.findByStudentIdAndAnsweredAtIsNotNullOrderByAnsweredAtAsc(studentCode);
        List<ReadingFeedbackRecord> readings = readingFeedbackRepo.findByStudentNameOrderByCreatedAtAsc(name);

        List<Map<String, Object>> timeline = buildUnifiedTimeline(quizRows, readings);
        List<String> chartLabels = new ArrayList<>();
        List<Integer> chartScores = new ArrayList<>();
        List<String> chartKinds = new ArrayList<>();
        for (Map<String, Object> p : timeline) {
            chartLabels.add((String) p.get("label"));
            chartScores.add((Integer) p.get("score"));
            chartKinds.add((String) p.get("kind"));
        }

        List<Map<String, Object>> mastery = new ArrayList<>();
        for (Object[] row : responseRepo.averageAccuracyBySubjectForStudent(studentCode)) {
            String subj = row[0] != null ? String.valueOf(row[0]) : "General";
            double pct = row[1] != null ? ((Number) row[1]).doubleValue() : 0;
            mastery.add(Map.of("subject", subj, "pct", Math.round(pct)));
        }

        long attTotal = attendanceRepo.countByActiveStudentName(name);
        long attPresent = attendanceRepo.countPresentByActiveStudentName(name);
        int attPct = attTotal == 0 ? 0 : (int) Math.round(100.0 * attPresent / attTotal);

        long correct = quizRows.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();

        String fallbackNote = buildFallbackTeacherNote(st, quizRows.size(), correct, attTotal, attPresent, attPct);
        int quizScorePct = quizRows.isEmpty() ? 0 : (int) Math.round(100.0 * correct / quizRows.size());
        ReadingLevel readingLevel = resolveReadingLevel(st, readings);

        StudentMetrics metrics = new StudentMetrics(attPct, quizScorePct, readingLevel);
        var resp = teacherInsightService.generateTeacherInsight(metrics);
        String en = resp.insightEnglish();
        String hi = resp.insightHindi();

        StreakResponse streak = streakService.generateStreakResponse(buildActivitiesForStreak(studentCode, name, quizRows, readings));

        out.put("studentCode", studentCode);
        out.put("studentName", name);
        out.put("chartLabels", chartLabels);
        out.put("chartScores", chartScores);
        out.put("chartKinds", chartKinds);
        out.put("scoreHistory", timeline);
        out.put("subjectMastery", mastery);
        out.put("attendancePct", attPct);
        out.put("totalResponses", quizRows.size());
        out.put("correctCount", correct);
        out.put("readingSessions", readings.size());
        out.put("teacherNoteEn", en);
        out.put("teacherNoteHi", hi);
        out.put("teacherNote", !hi.isBlank() ? hi : en);
        out.put("teacherNoteFallback", fallbackNote);
        out.put("streakCurrent", streak.currentStreak());
        out.put("streakLevel", streak.level().name());
        out.put("streakMessage", streak.message());
        return out;
    }

    private List<StudentActivity> buildActivitiesForStreak(String studentCode,
                                                             String studentName,
                                                             List<QuestionResponseEntity> quizRows,
                                                             List<ReadingFeedbackRecord> readings) {
        List<StudentActivity> activities = new ArrayList<>();
        if (studentCode == null || studentCode.isBlank()) return activities;

        // Quiz activity dates (based on answeredAt).
        for (QuestionResponseEntity r : quizRows) {
            Instant at = r.getAnsweredAt();
            if (at == null) continue;
            LocalDate d = at.atZone(ZoneId.systemDefault()).toLocalDate();
            activities.add(new StudentActivity(studentCode, d, StudentActivity.Type.QUIZ));
        }

        // Reading activity dates (based on createdAt).
        for (ReadingFeedbackRecord rf : readings) {
            Instant at = rf.getCreatedAt();
            if (at == null) continue;
            LocalDate d = at.atZone(ZoneId.systemDefault()).toLocalDate();
            activities.add(new StudentActivity(studentCode, d, StudentActivity.Type.READING));
        }

        // Attendance activity dates (any marked attendance day).
        // Note: AttendanceRecord stores LocalDate already.
        List<java.time.LocalDate> attDates =
                attendanceRepo.findActivityDatesByActiveStudentNameOrderByDateAsc(studentName);
        for (java.time.LocalDate d : attDates) {
            activities.add(new StudentActivity(studentCode, d, StudentActivity.Type.ATTENDANCE));
        }

        return activities;
    }

    private List<Map<String, Object>> buildUnifiedTimeline(List<QuestionResponseEntity> quizRows,
                                                           List<ReadingFeedbackRecord> readings) {
        List<TimelineEv> ev = new ArrayList<>();
        int qi = 1;
        for (QuestionResponseEntity r : quizRows) {
            Instant at = r.getAnsweredAt();
            if (at == null) continue;
            boolean ok = Boolean.TRUE.equals(r.getIsCorrect());
            ev.add(new TimelineEv(at, "quiz", ok ? 100 : 0, "Quiz Q" + (qi++)));
        }
        int ri = 1;
        for (ReadingFeedbackRecord rf : readings) {
            Instant at = rf.getCreatedAt();
            if (at == null) continue;
            int flu = rf.getFluencyScore() != null ? rf.getFluencyScore() : 5;
            int sc = Math.min(100, Math.max(0, flu * 10));
            ev.add(new TimelineEv(at, "reading", sc, "Reading " + (ri++)));
        }
        ev.sort(Comparator.comparing(TimelineEv::at));
        List<Map<String, Object>> list = new ArrayList<>();
        for (TimelineEv e : ev) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", e.at().toString());
            m.put("kind", e.kind());
            m.put("score", e.score());
            m.put("label", e.shortLabel() + " · " + TS.format(e.at()));
            list.add(m);
        }
        return list;
    }

    private record TimelineEv(Instant at, String kind, int score, String shortLabel) {}

    private String buildFallbackTeacherNote(Optional<StudentEntity> st, int quizN, long correct,
                                            long attTotal, long attPresent, int attPct) {
        StringBuilder note = new StringBuilder();
        note.append("Quiz responses recorded: ").append(quizN).append(". Correct: ").append(correct).append(". ");
        if (attTotal > 0) {
            note.append("Attendance (marked days): ").append(attPresent).append("/").append(attTotal).append(" (")
                    .append(attPct).append("%). ");
        } else {
            note.append("No attendance rows for this student name yet. ");
        }
        st.ifPresent(s -> {
            if (s.getStrength() != null && !s.getStrength().isBlank()) {
                note.append("Strength: ").append(s.getStrength().trim()).append(" ");
            }
            if (s.getWeakness() != null && !s.getWeakness().isBlank()) {
                note.append("Areas of Improvements: ").append(s.getWeakness().trim()).append(".");
            }
        });
        return note.toString().trim();
    }

    private ReadingLevel resolveReadingLevel(Optional<StudentEntity> st, List<ReadingFeedbackRecord> readings) {
        if (readings == null || readings.isEmpty()) {
            // If no reading evaluation is stored yet, use teacher-entered strength as a simple heuristic.
            if (st.isPresent()) {
                String strength = st.get().getStrength();
                if (strength != null && !strength.isBlank()) {
                    String lower = strength.toLowerCase();
                    boolean indicatesReading =
                            lower.contains("reading") ||
                                    lower.contains("fluency") ||
                                    strength.contains("पढ़") ||
                                    strength.contains("पढ़ना") ||
                                    strength.contains("रीडिंग") ||
                                    strength.contains("फ्लुए");
                    if (indicatesReading) return ReadingLevel.HIGH;
                }
            }
            return ReadingLevel.MEDIUM;
        }

        double sum = 0;
        int n = 0;
        for (ReadingFeedbackRecord rf : readings) {
            Integer acc = rf.getAccuracyPercent();
            if (acc != null) {
                sum += acc.doubleValue();
            } else {
                int flu = rf.getFluencyScore() != null ? rf.getFluencyScore() : 5;
                int sc = Math.min(100, Math.max(0, flu * 10));
                sum += sc;
            }
            n++;
        }

        if (n == 0) return ReadingLevel.MEDIUM;
        double avg = sum / n;
        if (avg < 50) return ReadingLevel.LOW;
        if (avg <= 75) return ReadingLevel.MEDIUM;
        return ReadingLevel.HIGH;
    }
}

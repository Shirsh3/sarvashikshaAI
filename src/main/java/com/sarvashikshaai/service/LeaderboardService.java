package com.sarvashikshaai.service;

import com.sarvashikshaai.model.dto.StudentActivity;
import com.sarvashikshaai.model.entity.QuestionResponseEntity;
import com.sarvashikshaai.model.entity.ReadingFeedbackRecord;
import com.sarvashikshaai.model.entity.StudentEntity;
import com.sarvashikshaai.repository.AttendanceRepository;
import com.sarvashikshaai.repository.QuestionResponseRepository;
import com.sarvashikshaai.repository.ReadingFeedbackRepository;
import com.sarvashikshaai.repository.StudentEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final QuestionResponseRepository questionResponseRepo;
    private final StudentEntityRepository studentRepo;
    private final AttendanceRepository attendanceRepo;
    private final ReadingFeedbackRepository readingRepo;
    private final StreakService streakService;

    // Points model (simple + predictable)
    private static final int ATTENDANCE_PRESENT_POINTS = 2; // per present day
    private static final int READING_SESSION_BASE_POINTS = 5; // per session
    private static final int READING_ACCURACY_BONUS_MAX = 5; // per session (0..5) based on avg accuracy

    public record LeaderboardRow(
            int rank,
            String studentId,
            String studentName,
            long totalPoints,
            long quizPoints,
            long readingPoints,
            long attendancePoints,
            long questionsAnswered,
            int accuracyPercent,
            int currentStreakDays
    ) {}

    public List<LeaderboardRow> buildLeaderboard() {
        Instant to = Instant.now();
        Instant from = to.minusSeconds(60L * 60 * 24 * 30); // fallback window
        return buildLeaderboard(from, to);
    }

    public List<LeaderboardRow> buildLeaderboard(Instant from, Instant to) {
        // Quiz aggregates keyed by studentId (code)
        Map<String, QuizAgg> quiz = new HashMap<>();
        for (Object[] row : questionResponseRepo.aggregateLeaderboardByStudentBetween(from, to)) {
            if (row == null || row.length < 4) continue;
            String studentId = row[0] != null ? row[0].toString() : "";
            long pts = row[1] instanceof Number n ? n.longValue() : 0L;
            long answered = row[2] instanceof Number n ? n.longValue() : 0L;
            long correct = row[3] instanceof Number n ? n.longValue() : 0L;
            quiz.put(studentId, new QuizAgg(pts, answered, correct));
        }

        // Attendance aggregates keyed by studentName
        ZoneId zone = ZoneId.systemDefault();
        LocalDate fromDate = from.atZone(zone).toLocalDate();
        LocalDate toDate = to.minusMillis(1).atZone(zone).toLocalDate();
        Map<String, Long> attPresentByName = new HashMap<>();
        for (Object[] row : attendanceRepo.countPresentDaysByStudentBetween(fromDate, toDate)) {
            if (row == null || row.length < 2) continue;
            String name = row[0] != null ? row[0].toString() : "";
            long n = row[1] instanceof Number num ? num.longValue() : 0L;
            if (!name.isBlank()) attPresentByName.put(name, n);
        }

        // Reading aggregates keyed by studentName
        Map<String, ReadingAgg> readingByName = new HashMap<>();
        for (Object[] row : readingRepo.aggregateReadingByStudentBetween(from, to)) {
            if (row == null || row.length < 3) continue;
            String name = row[0] != null ? row[0].toString() : "";
            long count = row[1] instanceof Number n ? n.longValue() : 0L;
            double avgAcc = row[2] instanceof Number n ? n.doubleValue() : 0.0;
            if (!name.isBlank()) readingByName.put(name, new ReadingAgg(count, avgAcc));
        }

        // Build rows for all active students (so attendance/reading-only still appear)
        List<StudentEntity> students = studentRepo.findByActiveTrue();
        List<LeaderboardRow> out = new ArrayList<>();
        for (StudentEntity st : students) {
            if (st == null) continue;
            String code = st.getCode() == null ? "" : st.getCode().trim();
            String name = st.getName() == null ? "" : st.getName().trim();
            if (code.isBlank() && name.isBlank()) continue;

            QuizAgg qa = quiz.getOrDefault(code, new QuizAgg(0, 0, 0));
            long quizPoints = qa.points();
            long answered = qa.answered();
            long correct = qa.correct();
            int acc = answered > 0 ? (int) Math.round(100.0 * correct / answered) : 0;

            long attPresent = attPresentByName.getOrDefault(name, 0L);
            long attendancePoints = attPresent * ATTENDANCE_PRESENT_POINTS;

            ReadingAgg ra = readingByName.get(name);
            long readingPoints = 0;
            if (ra != null && ra.count() > 0) {
                int bonus = (int) Math.max(0, Math.min(READING_ACCURACY_BONUS_MAX, Math.floor(ra.avgAccuracyPercent() / 20.0)));
                readingPoints = ra.count() * (READING_SESSION_BASE_POINTS + bonus);
            }

            int streakDays = computeCurrentStreakAllTime(code, name);

            long total = quizPoints + readingPoints + attendancePoints;
            out.add(new LeaderboardRow(0, code.isBlank() ? name : code, name.isBlank() ? code : name, total,
                    quizPoints, readingPoints, attendancePoints, answered, acc, streakDays));
        }

        // Do not call .reversed() on the result of .thenComparingLong — it reverses the *entire*
        // comparator and flips primary sort (lowest total would appear first).
        out.sort(Comparator
                .comparingLong(LeaderboardRow::totalPoints).reversed()
                .thenComparing(Comparator.comparingLong(LeaderboardRow::quizPoints).reversed())
                .thenComparing(LeaderboardRow::studentName));

        int rank = 1;
        List<LeaderboardRow> ranked = new ArrayList<>();
        for (LeaderboardRow r : out) {
            ranked.add(new LeaderboardRow(rank++, r.studentId(), r.studentName(), r.totalPoints(),
                    r.quizPoints(), r.readingPoints(), r.attendancePoints(),
                    r.questionsAnswered(), r.accuracyPercent(), r.currentStreakDays()));
        }
        return ranked;
    }

    private int computeCurrentStreakAllTime(String studentCode, String studentName) {
        try {
            List<StudentActivity> activities = new ArrayList<>();
            if (studentCode != null && !studentCode.isBlank()) {
                List<QuestionResponseEntity> quizRows =
                        questionResponseRepo.findByStudentIdAndAnsweredAtIsNotNullOrderByAnsweredAtAsc(studentCode);
                for (QuestionResponseEntity r : quizRows) {
                    Instant at = r.getAnsweredAt();
                    if (at == null) continue;
                    LocalDate d = at.atZone(ZoneId.systemDefault()).toLocalDate();
                    activities.add(new StudentActivity(studentCode, d, StudentActivity.Type.QUIZ));
                }
            }

            if (studentName != null && !studentName.isBlank()) {
                List<ReadingFeedbackRecord> readings = readingRepo.findByStudentNameOrderByCreatedAtAsc(studentName);
                for (ReadingFeedbackRecord rf : readings) {
                    Instant at = rf.getCreatedAt();
                    if (at == null) continue;
                    LocalDate d = at.atZone(ZoneId.systemDefault()).toLocalDate();
                    activities.add(new StudentActivity(studentCode == null || studentCode.isBlank() ? studentName : studentCode, d, StudentActivity.Type.READING));
                }
                List<LocalDate> attDates = attendanceRepo.findActivityDatesByStudentNameOrderByDateAsc(studentName);
                for (LocalDate d : attDates) {
                    activities.add(new StudentActivity(studentCode == null || studentCode.isBlank() ? studentName : studentCode, d, StudentActivity.Type.ATTENDANCE));
                }
            }
            return streakService.calculateCurrentStreak(activities);
        } catch (Exception e) {
            return 0;
        }
    }

    private record QuizAgg(long points, long answered, long correct) {}
    private record ReadingAgg(long count, double avgAccuracyPercent) {}
}

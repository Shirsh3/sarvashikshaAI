package com.sarvashikshaai.service;

import com.sarvashikshaai.repository.AttendanceRepository;
import com.sarvashikshaai.repository.QuestionResponseRepository;
import com.sarvashikshaai.repository.QuizRepository;
import com.sarvashikshaai.repository.ReadingFeedbackRepository;
import com.sarvashikshaai.repository.StudentEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final StudentEntityRepository studentRepo;
    private final QuizRepository quizRepo;
    private final QuestionResponseRepository questionResponseRepo;
    private final ReadingFeedbackRepository readingFeedbackRepo;
    private final AttendanceRepository attendanceRepo;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    @Transactional(readOnly = true)
    public Map<String, Object> buildSummary(LocalDate from, LocalDate to, String grade) {
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInst = from.atStartOfDay(zone).toInstant();
        Instant toInst = to.plusDays(1).atStartOfDay(zone).toInstant();

        long students = studentRepo.countActiveForGrade(grade);
        long quizzes = quizRepo.countCreatedBetweenForGrade(fromInst, toInst, grade);
        long responses = questionResponseRepo.countAnsweredBetweenForStudentGrade(fromInst, toInst, grade);
        long attendanceRows = attendanceRepo.countByDateRangeAndGrade(from, to, grade);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statStudents", students);
        m.put("statQuizzes", quizzes);
        m.put("statResponses", responses);
        m.put("statAttendanceRows", attendanceRows);
        m.put("filterDateFrom", from.toString());
        m.put("filterDateTo", to.toString());
        m.put("filterGrade", grade != null ? grade : "");
        return m;
    }

    /**
     * Attendance trend (% present per day) and activity mix (quiz answers vs reading sessions vs attendance marks).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildCharts(LocalDate from, LocalDate to, String grade) {
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInst = from.atStartOfDay(zone).toInstant();
        Instant toInst = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<String> trendLabels = new ArrayList<>();
        List<Double> trendValues = new ArrayList<>();
        for (Object[] row : attendanceRepo.dailyPresentPercentByGrade(from, to, grade)) {
            if (row[0] == null) continue;
            LocalDate d = row[0] instanceof LocalDate ld ? ld : LocalDate.parse(row[0].toString());
            trendLabels.add(DAY.format(d));
            double pct = row[1] instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(row[1]));
            trendValues.add(Math.round(pct * 10.0) / 10.0);
        }

        long quizActivity = questionResponseRepo.countAnsweredBetweenForStudentGrade(fromInst, toInst, grade);
        long readingActivity = readingFeedbackRepo.countCreatedBetweenForGrade(fromInst, toInst, grade);
        long attendanceActivity = attendanceRepo.countByDateRangeAndGrade(from, to, grade);

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("labels", List.of("Quiz answers", "Reading sessions", "Attendance marks"));
        activity.put("values", List.of(quizActivity, readingActivity, attendanceActivity));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("attendanceTrend", Map.of("labels", trendLabels, "values", trendValues));
        out.put("activityMix", activity);
        return out;
    }
}

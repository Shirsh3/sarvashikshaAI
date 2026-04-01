package com.sarvashikshaai.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.model.entity.StudentEntity;
import com.sarvashikshaai.repository.AttendanceRepository;
import com.sarvashikshaai.repository.GradeRefRepository;
import com.sarvashikshaai.repository.QuestionResponseRepository;
import com.sarvashikshaai.repository.QuizRepository;
import com.sarvashikshaai.repository.StudentEntityRepository;
import com.sarvashikshaai.service.AdminAnalyticsService;
import com.sarvashikshaai.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StudentEntityRepository studentRepo;
    private final QuizRepository quizRepo;
    private final QuestionResponseRepository questionResponseRepo;
    private final AttendanceRepository attendanceRepo;
    private final GradeRefRepository gradeRefRepository;
    private final AdminAnalyticsService adminAnalyticsService;
    private final AdminDashboardService adminDashboardService;
    private final ObjectMapper objectMapper;

    @GetMapping({"", "/"})
    public String dashboard(
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "month") String range,
            @RequestParam(required = false) String grade) {

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);
        String rangeValue = (range == null || range.isBlank()) ? "month" : range.trim().toLowerCase();
        if ("today".equals(rangeValue)) {
            from = to;
        } else if ("yesterday".equals(rangeValue)) {
            to = to.minusDays(1);
            from = to;
        } else if ("tomorrow".equals(rangeValue)) {
            to = to.plusDays(1);
            from = to;
        } else if ("week".equals(rangeValue)) {
            from = to.minusDays(6);
        } else if ("month".equals(rangeValue)) {
            from = to.minusDays(29);
        } else if ("custom".equals(rangeValue)) {
            to = dateTo != null ? dateTo : LocalDate.now();
            from = dateFrom != null ? dateFrom : to.minusDays(30);
        } else {
            rangeValue = "month";
        }
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        String gradeParam = normalizeGrade(grade);
        ZoneId zone = ZoneId.systemDefault();
        var fromInst = from.atStartOfDay(zone).toInstant();
        var toInst = to.plusDays(1).atStartOfDay(zone).toInstant();

        long students = studentRepo.countActiveForGrade(gradeParam);
        long quizzes = quizRepo.countCreatedBetweenForGrade(fromInst, toInst, gradeParam);
        long responses = questionResponseRepo.countAnsweredBetweenForStudentGrade(fromInst, toInst, gradeParam);
        long attendanceRows = attendanceRepo.countByDateRangeAndGrade(from, to, gradeParam);

        model.addAttribute("statStudents", students);
        model.addAttribute("statQuizzes", quizzes);
        model.addAttribute("statResponses", responses);
        model.addAttribute("statAttendanceRows", attendanceRows);
        model.addAttribute("filterDateFrom", from);
        model.addAttribute("filterDateTo", to);
        model.addAttribute("filterGrade", grade != null ? grade : "");
        model.addAttribute("filterRange", rangeValue);
        model.addAttribute("gradeOptions", gradeRefRepository.findAllByOrderBySortOrderAsc());
        return "admin/dashboard";
    }

    /** Canonical grades (KG, UKG, 1–12) for filters and dropdowns */
    @GetMapping("/api/grades")
    @ResponseBody
    public List<Map<String, String>> gradesApi() {
        List<Map<String, String>> list = new ArrayList<>();
        gradeRefRepository.findAllByOrderBySortOrderAsc().forEach(g -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", g.getCode());
            m.put("label", g.getLabel());
            list.add(m);
        });
        return list;
    }

    /** Dashboard stats as JSON (same filters as GET /admin) */
    @GetMapping("/api/dashboard/summary")
    @ResponseBody
    public Map<String, Object> dashboardSummaryApi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "month") String range,
            @RequestParam(required = false) String grade) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);
        String rangeValue = (range == null || range.isBlank()) ? "month" : range.trim().toLowerCase();
        if ("today".equals(rangeValue)) {
            from = to;
        } else if ("yesterday".equals(rangeValue)) {
            to = to.minusDays(1);
            from = to;
        } else if ("tomorrow".equals(rangeValue)) {
            to = to.plusDays(1);
            from = to;
        } else if ("week".equals(rangeValue)) {
            from = to.minusDays(6);
        } else if ("month".equals(rangeValue)) {
            from = to.minusDays(29);
        } else if ("custom".equals(rangeValue)) {
            to = dateTo != null ? dateTo : LocalDate.now();
            from = dateFrom != null ? dateFrom : to.minusDays(30);
        }
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        return adminDashboardService.buildSummary(from, to, normalizeGrade(grade));
    }

    @GetMapping("/api/dashboard/charts")
    @ResponseBody
    public Map<String, Object> dashboardChartsApi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "month") String range,
            @RequestParam(required = false) String grade) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);
        String rangeValue = (range == null || range.isBlank()) ? "month" : range.trim().toLowerCase();
        if ("today".equals(rangeValue)) {
            from = to;
        } else if ("yesterday".equals(rangeValue)) {
            to = to.minusDays(1);
            from = to;
        } else if ("tomorrow".equals(rangeValue)) {
            to = to.plusDays(1);
            from = to;
        } else if ("week".equals(rangeValue)) {
            from = to.minusDays(6);
        } else if ("month".equals(rangeValue)) {
            from = to.minusDays(29);
        } else if ("custom".equals(rangeValue)) {
            to = dateTo != null ? dateTo : LocalDate.now();
            from = dateFrom != null ? dateFrom : to.minusDays(30);
        }
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        return adminDashboardService.buildCharts(from, to, normalizeGrade(grade));
    }

    /** Prefix search on student name (starts-with); optional grade filter */
    @GetMapping("/api/students/search")
    @ResponseBody
    public List<Map<String, String>> searchStudentsApi(
            @RequestParam String q,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "20") int limit) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String prefix = q.trim();
        if (prefix.length() > 120) {
            prefix = prefix.substring(0, 120);
        }
        int lim = Math.min(Math.max(limit, 1), 50);
        var page = PageRequest.of(0, lim);
        List<Map<String, String>> out = new ArrayList<>();
        for (StudentEntity s : studentRepo.searchActiveByNamePrefix(prefix, normalizeGrade(grade), page)) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", s.getCode() != null ? s.getCode() : "");
            m.put("name", s.getName());
            m.put("grade", s.getGrade() != null ? s.getGrade() : "");
            out.add(m);
        }
        return out;
    }

    @GetMapping("/analytics")
    public String analytics(
            Model model,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false, defaultValue = "month") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) throws JsonProcessingException {
        model.addAttribute("pageTitle", "Backoffice analytics");

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);
        String rangeValue = (range == null || range.isBlank()) ? "month" : range.trim().toLowerCase();
        if ("today".equals(rangeValue)) {
            from = to;
        } else if ("yesterday".equals(rangeValue)) {
            to = to.minusDays(1);
            from = to;
        } else if ("tomorrow".equals(rangeValue)) {
            to = to.plusDays(1);
            from = to;
        } else if ("week".equals(rangeValue)) {
            from = to.minusDays(6);
        } else if ("2month".equals(rangeValue)) {
            from = to.minusDays(59);
        } else if ("4month".equals(rangeValue)) {
            from = to.minusDays(119);
        } else if ("custom".equals(rangeValue)) {
            to = dateTo != null ? dateTo : LocalDate.now();
            from = dateFrom != null ? dateFrom : to.minusDays(29);
        } else {
            rangeValue = "month";
        }
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        var zone = ZoneId.systemDefault();
        var fromInst = from.atStartOfDay(zone).toInstant();
        var toInst = to.plusDays(1).atStartOfDay(zone).toInstant();

        String gradeParam = normalizeGrade(grade);

        long answered = questionResponseRepo.countAnsweredBetweenForStudentGrade(fromInst, toInst, gradeParam);
        long correct = questionResponseRepo.countAnsweredCorrectBetweenForStudentGrade(fromInst, toInst, gradeParam);
        int avgQuiz = answered == 0 ? 0 : (int) Math.round(100.0 * correct / answered);

        long attAll = attendanceRepo.countByDateRangeAndGrade(from, to, gradeParam);
        long attPresent = attendanceRepo.countPresentByDateRangeAndGrade(from, to, gradeParam);
        int attPct = attAll == 0 ? 0 : (int) Math.round(100.0 * attPresent / attAll);

        model.addAttribute("overviewAvgQuiz", avgQuiz);
        model.addAttribute("overviewAttendance", attPct);
        model.addAttribute("overviewBadges", answered == 0 ? 0 : (int) Math.min(99, correct / 2));
        model.addAttribute("overviewStreak", "—");
        model.addAttribute("gradeFilter", grade != null ? grade : "");
        model.addAttribute("filterRange", rangeValue);
        model.addAttribute("filterDateFrom", from);
        model.addAttribute("filterDateTo", to);
        model.addAttribute("gradeOptions", gradeRefRepository.findAllByOrderBySortOrderAsc());
        model.addAttribute("studentsJson", objectMapper.writeValueAsString(List.of()));
        return "admin/analytics";
    }

    /**
     * Per-student chart + subject mastery + attendance + AI teacher insight.
     */
    @GetMapping("/api/analytics/student")
    @ResponseBody
    public Map<String, Object> studentAnalyticsApi(@RequestParam String code) {
        if (code == null || code.isBlank()) {
            return Map.of("error", "Student code is required.");
        }
        return adminAnalyticsService.buildStudentAnalytics(code.trim());
    }

    @GetMapping("/students")
    public String students(Model model) {
        model.addAttribute("students", studentRepo.findByActiveTrue());
        return "admin/students";
    }

    private static String normalizeGrade(String grade) {
        if (grade == null) {
            return null;
        }
        String t = grade.trim();
        return t.isEmpty() ? null : t;
    }
}

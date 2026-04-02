package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.Student;
import com.sarvashikshaai.repository.AttendanceRepository;
import com.sarvashikshaai.repository.GradeRefRepository;
import com.sarvashikshaai.repository.QuestionResponseRepository;
import com.sarvashikshaai.repository.QuizRepository;
import com.sarvashikshaai.repository.ReadingFeedbackRepository;
import com.sarvashikshaai.service.AssemblyConfigService;
import com.sarvashikshaai.service.StudentCrudService;
import com.sarvashikshaai.service.ThoughtConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.ZoneId;

@Controller
@RequestMapping("/teacher")
@RequiredArgsConstructor
@Slf4j
public class TeacherPanelController {

    private final StudentCrudService studentCrudService;
    private final AssemblyConfigService assemblyConfigService;
    private final ThoughtConfigService thoughtConfigService;
    private final GradeRefRepository gradeRefRepository;
    private final QuizRepository quizRepository;
    private final ReadingFeedbackRepository readingFeedbackRepository;
    private final QuestionResponseRepository questionResponseRepository;
    private final AttendanceRepository attendanceRepository;

    @GetMapping
    public String teacherHome() {
        return "redirect:/teacher/setup";
    }

    /** Overview hub (sidebar “Dashboard”); distinct from {@link #teacherHome()} redirect target. */
    @GetMapping("/dashboard")
    public String teacherDashboard(
            Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "week") String range,
            @RequestParam(required = false) String grade) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(6);
        String rangeValue = (range == null || range.isBlank()) ? "week" : range.trim().toLowerCase();
        if ("today".equals(rangeValue)) {
            from = to;
        } else if ("yesterday".equals(rangeValue)) {
            to = to.minusDays(1);
            from = to;
        } else if ("tomorrow".equals(rangeValue)) {
            to = to.plusDays(1);
            from = to;
        } else if ("month".equals(rangeValue)) {
            from = to.minusDays(29);
        } else if ("custom".equals(rangeValue)) {
            to = dateTo != null ? dateTo : LocalDate.now();
            from = dateFrom != null ? dateFrom : to.minusDays(6);
        } else {
            rangeValue = "week";
        }
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        String gradeFilter = (grade != null && !grade.isBlank()) ? grade.trim() : null;
        var zone = ZoneId.systemDefault();
        var fromInst = from.atStartOfDay(zone).toInstant();
        var toInst = to.plusDays(1).atStartOfDay(zone).toInstant();

        long answered = questionResponseRepository.countAnsweredBetweenForStudentGrade(fromInst, toInst, gradeFilter);
        long correct = questionResponseRepository.countAnsweredCorrectBetweenForStudentGrade(fromInst, toInst, gradeFilter);
        int quizAccuracy = answered == 0 ? 0 : (int) Math.round((100.0 * correct) / answered);

        long attendanceAll = attendanceRepository.countByDateRangeAndGrade(from, to, gradeFilter);
        long attendancePresent = attendanceRepository.countPresentByDateRangeAndGrade(from, to, gradeFilter);
        int attendancePct = attendanceAll == 0 ? 0 : (int) Math.round((100.0 * attendancePresent) / attendanceAll);

        long points = questionResponseRepository.countAnsweredCorrectBetweenForStudentGrade(fromInst, toInst, gradeFilter);

        model.addAttribute("quizAccuracy", quizAccuracy);
        model.addAttribute("attendancePct", attendancePct);
        model.addAttribute("studentsPresentCount", attendancePresent);
        model.addAttribute("points", points);
        model.addAttribute("recentQuizzes", quizRepository.findAllByOrderByCreatedAtDesc().stream().limit(5).toList());
        model.addAttribute("recentReadings", readingFeedbackRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .limit(5)
                .toList());
        model.addAttribute("filterDateFrom", from);
        model.addAttribute("filterDateTo", to);
        model.addAttribute("filterRange", rangeValue);
        model.addAttribute("filterGrade", gradeFilter == null ? "" : gradeFilter);
        model.addAttribute("gradeOptions", gradeRefRepository.findAllByOrderBySortOrderAsc());
        return "teacher/dashboard";
    }

    /** Dedicated learning hub (grade presets, links to reading/quiz, voice prep). */
    @GetMapping("/learning")
    public String teacherLearning(Model model) {
        model.addAttribute("gradeOptions", gradeRefRepository.findAllByOrderBySortOrderAsc());
        model.addAttribute("quizList", quizRepository.findAllByOrderByCreatedAtDesc());
        return "teacher/learning";
    }

    @GetMapping("/setup")
    public String setupPage(Model model, @RequestParam(value = "edit", required = false) String editName) {
        model.addAttribute("teacherName", "Teacher");
        model.addAttribute("students", studentCrudService.getAllActive());
        model.addAttribute("gradeOptions", gradeRefRepository.findAllByOrderBySortOrderAsc());

        // If we came here after a validation error, a pre-filled studentForm (and editing flag)
        // will already be present as flash attributes — do not overwrite it.
        if (!model.containsAttribute("studentForm")) {
            Student form;
            if (editName != null && !editName.isBlank()) {
                form = studentCrudService.findByName(editName).orElseGet(Student::new);
                if (form.getName() != null) {
                    form.setOriginalName(form.getName());
                }
                model.addAttribute("editing", editName);
            } else {
                form = new Student();
                form.setActive(true);
            }
            model.addAttribute("studentForm", form);
        }

        // If there was no flash "editing", fall back to query param (for normal Edit button flow)
        if (!model.containsAttribute("editing")) {
            model.addAttribute("editing", editName);
        }

        // Assembly config
        var cfg = assemblyConfigService.getOrCreate();
        model.addAttribute("assemblyConfig", cfg);
        model.addAttribute("assemblySlotOrderCsv", assemblyConfigService.getResolvedSlotOrderCsv(cfg));

        return "teacher/setup";
    }

    @PostMapping("/students/save")
    public String saveStudent(@ModelAttribute("studentForm") Student student,
                              RedirectAttributes redirectAttributes) {
        try {
            if (student.getName() != null) {
                student.setName(student.getName().trim());
            }
            studentCrudService.createOrUpdate(student);
            boolean isEdit = student.getOriginalName() != null && !student.getOriginalName().isBlank();
            redirectAttributes.addFlashAttribute("infoMessage", isEdit ? "Student updated." : "Student added.");
        } catch (IllegalArgumentException ex) {
            log.warn("Failed to save student: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("studentForm", student);
            String editing = (student.getOriginalName() != null && !student.getOriginalName().isBlank())
                    ? student.getOriginalName()
                    : null;
            if (editing != null) {
                redirectAttributes.addFlashAttribute("editing", editing);
            }
        } catch (DataIntegrityViolationException ex) {
            // DB-level unique constraint (e.g. duplicate student code). Show friendly error instead of breaking flow.
            log.warn("Failed to save student (db constraint): {}", ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Student code is already in use. Please choose a different code.");
            redirectAttributes.addFlashAttribute("studentForm", student);
            String editing = (student.getOriginalName() != null && !student.getOriginalName().isBlank())
                    ? student.getOriginalName()
                    : null;
            if (editing != null) {
                redirectAttributes.addFlashAttribute("editing", editing);
            }
        }
        return "redirect:/teacher/setup";
    }

    @PostMapping("/assembly/save")
    public String saveAssemblyLinks(@RequestParam(required = false) String anthemUrl,
                                    @RequestParam(required = false) String prayerUrl,
                                    @RequestParam(required = false) String pledgeUrl,
                                    @RequestParam(required = false) String hindiPrayerUrl,
                                    @RequestParam(required = false) String slotOrder,
                                    RedirectAttributes redirectAttributes) {
        assemblyConfigService.updateUrls(anthemUrl, prayerUrl, pledgeUrl, hindiPrayerUrl, slotOrder);
        redirectAttributes.addFlashAttribute("assemblySaved", true);
        return "redirect:/teacher/setup?view=assembly";
    }


    @PostMapping("/students/delete")
    public String deleteStudent(@RequestParam("name") String name,
                                RedirectAttributes redirectAttributes) {
        try {
            studentCrudService.deleteByName(name);
            redirectAttributes.addFlashAttribute("infoMessage", "Student removed (marked inactive).");
        } catch (DataIntegrityViolationException ex) {
            log.warn("Failed to delete student (db constraint): {}", ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to remove this student because they already have activity.");
        } catch (Exception ex) {
            log.warn("Failed to delete student: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to remove this student right now.");
        }
        return "redirect:/teacher/setup";
    }
}


package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.Student;
import com.sarvashikshaai.service.AssemblyConfigService;
import com.sarvashikshaai.service.StudentCrudService;
import com.sarvashikshaai.service.ThoughtConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/teacher")
@RequiredArgsConstructor
@Slf4j
public class TeacherPanelController {

    private final StudentCrudService studentCrudService;
    private final AssemblyConfigService assemblyConfigService;
    private final ThoughtConfigService thoughtConfigService;

    @GetMapping
    public String teacherHome() {
        return "redirect:/teacher/setup";
    }

    @GetMapping("/setup")
    public String setupPage(Model model, @RequestParam(value = "edit", required = false) String editName) {
        model.addAttribute("teacherName", "Teacher");
        model.addAttribute("students", studentCrudService.getAllActive());

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
        }
        return "redirect:/teacher/setup";
    }

    @PostMapping("/assembly/save")
    public String saveAssemblyLinks(@RequestParam(required = false) String anthemUrl,
                                    @RequestParam(required = false) String prayerUrl,
                                    @RequestParam(required = false) String pledgeUrl,
                                    @RequestParam(required = false) String hindiPrayerUrl) {
        assemblyConfigService.updateUrls(anthemUrl, prayerUrl, pledgeUrl, hindiPrayerUrl);
        return "redirect:/teacher/setup";
    }


    @PostMapping("/students/delete")
    public String deleteStudent(@RequestParam("name") String name) {
        studentCrudService.deleteByName(name);
        return "redirect:/teacher/setup";
    }
}


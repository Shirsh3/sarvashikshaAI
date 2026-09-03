package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.entity.ClassMaterialEntity;
import com.sarvashikshaai.repository.GradeRefRepository;
import com.sarvashikshaai.service.ClassMaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/teacher/materials")
@RequiredArgsConstructor
@Slf4j
public class ClassMaterialController {

    private final ClassMaterialService classMaterialService;
    private final GradeRefRepository gradeRefRepository;

    @GetMapping
    public String page(
            Model model,
            @RequestParam(required = false) String grade
    ) {
        model.addAttribute("gradeOptions", gradeRefRepository.findAllByOrderBySortOrderAsc());
        model.addAttribute("filterGrade", grade == null ? "" : grade.trim());
        model.addAttribute("materials", classMaterialService.listByGrade(grade));
        return "teacher/materials";
    }

    /** JSON list of READY chapters for Learning explain picker. */
    @GetMapping("/api/ready")
    @ResponseBody
    public List<Map<String, Object>> readyChapters(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String subject
    ) {
        return classMaterialService.listReady(grade, subject).stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("title", m.getTitle());
            row.put("grade", m.getGrade());
            row.put("subject", m.getSubject());
            row.put("chunkCount", m.getChunkCount());
            return row;
        }).toList();
    }

    /** Lightweight status poll for indexing UI (PENDING / INDEXING / READY / FAILED). */
    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Object> materialStatus(@RequestParam(required = false) String grade) {
        List<ClassMaterialEntity> list = classMaterialService.listByGrade(grade);
        List<Map<String, Object>> materials = list.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("title", m.getTitle());
            row.put("status", m.getStatus());
            row.put("chunkCount", m.getChunkCount());
            row.put("errorMessage", m.getErrorMessage());
            return row;
        }).toList();
        long indexing = materials.stream()
                .filter(r -> {
                    String s = String.valueOf(r.get("status"));
                    return "PENDING".equals(s) || "INDEXING".equals(s);
                })
                .count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("materials", materials);
        out.put("indexingCount", indexing);
        out.put("indexing", indexing > 0);
        return out;
    }

    @PostMapping("/upload")
    public String upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String grade,
            @RequestParam(required = false, defaultValue = "GENERAL") String subject,
            @RequestParam(required = false, defaultValue = "") String title,
            RedirectAttributes redirectAttrs
    ) {
        try {
            String user = currentUsername();
            ClassMaterialEntity saved = classMaterialService.createPendingUpload(file, grade, subject, title, user);
            classMaterialService.startIndexing(saved.getId());
            redirectAttrs.addFlashAttribute("success",
                    "Uploaded “" + saved.getTitle() + "”. Indexing embeddings in the background…");
        } catch (Exception e) {
            log.warn("Material upload failed: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", e.getMessage() == null ? "Upload failed" : e.getMessage());
        }
        if (grade != null && !grade.isBlank()) {
            return "redirect:/teacher/materials?grade=" + grade.trim();
        }
        return "redirect:/teacher/materials";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam(required = false) String grade, RedirectAttributes redirectAttrs) {
        try {
            classMaterialService.delete(id);
            redirectAttrs.addFlashAttribute("success", "Material deleted.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("error", "Could not delete material.");
        }
        if (grade != null && !grade.isBlank()) {
            return "redirect:/teacher/materials?grade=" + grade.trim();
        }
        return "redirect:/teacher/materials";
    }

    @PostMapping("/{id}/reindex")
    public String reindex(@PathVariable Long id, @RequestParam(required = false) String grade, RedirectAttributes redirectAttrs) {
        classMaterialService.startIndexing(id);
        redirectAttrs.addFlashAttribute("success", "Re-indexing started.");
        if (grade != null && !grade.isBlank()) {
            return "redirect:/teacher/materials?grade=" + grade.trim();
        }
        return "redirect:/teacher/materials";
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return "unknown";
        return auth.getName();
    }
}

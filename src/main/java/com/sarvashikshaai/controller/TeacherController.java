package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.TeachingRequest;
import com.sarvashikshaai.model.TeachingResponse;
import com.sarvashikshaai.service.TeachingService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TeacherController {

    private final TeachingService teachingService;

    public TeacherController(TeachingService teachingService) {
        this.teachingService = teachingService;
    }

    @GetMapping("/")
    public String index(Model model) {
        // Flash attributes from the POST-Redirect-GET land here automatically.
        // Only set defaults when they weren't populated by a redirect.
        if (!model.containsAttribute("teachingRequest")) {
            model.addAttribute("teachingRequest", new TeachingRequest());
        }
        if (!model.containsAttribute("response")) {
            model.addAttribute("response", null);
        }
        return "index";
    }

    /**
     * Process the explanation, then redirect back to "/" so the browser
     * always shows http://localhost:8080/ regardless of which button was pressed.
     */
    @PostMapping("/explain")
    public String explain(@Valid @ModelAttribute("teachingRequest") TeachingRequest request,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            redirectAttrs.addFlashAttribute("teachingRequest", request);
            redirectAttrs.addFlashAttribute("response", null);
            return "redirect:/";
        }

        TeachingResponse response = teachingService.generateExplanation(request);
        redirectAttrs.addFlashAttribute("teachingRequest", request);
        redirectAttrs.addFlashAttribute("response", response);
        return "redirect:/";
    }
}

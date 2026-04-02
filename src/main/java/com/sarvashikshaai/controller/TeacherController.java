package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.TeachingRequest;
import com.sarvashikshaai.model.TeachingResponse;
import com.sarvashikshaai.service.TeachingService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TeacherController {

    private final TeachingService teachingService;

    public TeacherController(TeachingService teachingService) {
        this.teachingService = teachingService;
    }

    @GetMapping("/")
    public String index(Model model) {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "redirect:/login";
        }
        boolean isSuperAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (isSuperAdmin) return "redirect:/superadmin";
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) return "redirect:/admin";
        boolean isTeacher = auth.getAuthorities().stream().anyMatch(a -> "ROLE_TEACHER".equals(a.getAuthority()));
        if (isTeacher) return "redirect:/teacher/dashboard";
        if (!model.containsAttribute("teachingRequest")) {
            model.addAttribute("teachingRequest", new TeachingRequest());
        }
        model.addAttribute("response", null);
        return "redirect:/teacher/dashboard";
    }

    /**
     * Process the explanation, then redirect back to "/" so the browser
     * always shows http://localhost:8080/ regardless of which button was pressed.
     */
    @PostMapping("/explain")
    public String explain(@Valid @ModelAttribute("teachingRequest") TeachingRequest request,
                          BindingResult bindingResult,
                          @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                          Model model,
                          RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            if ("fetch".equalsIgnoreCase(requestedWith)) {
                model.addAttribute("teachingRequest", request);
                model.addAttribute("response", null);
                return "index";
            } else {
                redirectAttrs.addFlashAttribute("teachingRequest", request);
                redirectAttrs.addFlashAttribute("response", null);
                return "redirect:/";
            }
        }

        long t0 = System.currentTimeMillis();
        TeachingResponse response;
        try {
            response = teachingService.generateExplanation(request);
        } catch (Exception e) {
            long dt = System.currentTimeMillis() - t0;
            org.slf4j.LoggerFactory.getLogger(TeacherController.class)
                    .warn("/explain failed after {} ms: {}", dt, e.getMessage());
            response = new TeachingResponse(
                    "Unable to fetch explanation right now. Please try again.",
                    null, null, null, null, null, null, true);
        }
        if ("fetch".equalsIgnoreCase(requestedWith)) {
            model.addAttribute("teachingRequest", request);
            model.addAttribute("response", response);
            return "index";
        } else {
            redirectAttrs.addFlashAttribute("teachingRequest", request);
            redirectAttrs.addFlashAttribute("response", response);
            return "redirect:/";
        }
    }
}

package com.sarvashikshaai.controller;

import com.sarvashikshaai.service.AssemblyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/assembly")
@RequiredArgsConstructor
public class AssemblyController {

    private final AssemblyService assemblyService;

    @GetMapping
    public String assembly(@AuthenticationPrincipal OAuth2User principal, Model model) {

        // ── Thought of the Day ────────────────────────────────────────────────
        String teacherEmail = principal != null ? principal.getAttribute("email") : null;
        AssemblyService.DailyThought thought = assemblyService.getDailyThought(teacherEmail);
        model.addAttribute("thoughtHi", thought.hindi());
        model.addAttribute("thoughtEn", thought.english());

        // ── Static text (from JSON) ───────────────────────────────────────────
        AssemblyService.AssemblyContent content = assemblyService.getAssemblyContent();
        model.addAttribute("anthem",        content.anthem().text());
        model.addAttribute("anthemMeaning", content.anthem().meaning());
        model.addAttribute("pledge",        content.pledge().text());
        model.addAttribute("prayer",        content.prayer().text());
        model.addAttribute("prayerMeaning", content.prayer().meaning());

        // ── YouTube links from teacher's sheet ────────────────────────────────
        Map<String, String> links = assemblyService.getAssemblyLinks(teacherEmail);

        model.addAttribute("videoAnthem", videoId(links, AssemblyService.KEY_ANTHEM));
        model.addAttribute("videoPrayer", videoId(links, AssemblyService.KEY_PRAYER));
        model.addAttribute("videoPledge", videoId(links, AssemblyService.KEY_PLEDGE));
        model.addAttribute("videoHindi",  videoId(links, AssemblyService.KEY_HINDI));

        return "assembly";
    }

    private String videoId(Map<String, String> links, String key) {
        String url = links.get(key);
        return AssemblyService.extractVideoId(url);
    }
}

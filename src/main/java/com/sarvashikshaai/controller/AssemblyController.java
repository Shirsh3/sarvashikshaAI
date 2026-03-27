package com.sarvashikshaai.controller;

import com.sarvashikshaai.service.AssemblyService;
import com.sarvashikshaai.service.ThoughtConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/assembly")
@RequiredArgsConstructor
public class AssemblyController {

    private final AssemblyService assemblyService;
    private final ThoughtConfigService thoughtConfigService;

    @GetMapping
    public String assembly(Model model) {

        // ── Thought + Word + Habit of the Day ─────────────────────────────────
        AssemblyService.DailyThought thought = assemblyService.getDailyThought(null);
        model.addAttribute("thoughtHi", thought.thoughtHi());
        model.addAttribute("thoughtEn", thought.thoughtEn());
        model.addAttribute("wordEnglish", thought.wordEnglish());
        model.addAttribute("wordHindi", thought.wordHindi());
        model.addAttribute("habit", thought.habit());

        // ── Static text (from JSON, used internally only) ────────────────────
        AssemblyService.AssemblyContent content = assemblyService.getAssemblyContent();
        model.addAttribute("anthem",        content.anthem().text());
        model.addAttribute("anthemMeaning", content.anthem().meaning());
        model.addAttribute("pledge",        content.pledge().text());
        model.addAttribute("prayer",        content.prayer().text());
        model.addAttribute("prayerMeaning", content.prayer().meaning());

        // ── YouTube links from config ────────────────────────────────────────
        Map<String, String> links = assemblyService.getAssemblyLinks(null);

        model.addAttribute("videoAnthem", videoId(links, AssemblyService.KEY_ANTHEM));
        model.addAttribute("videoPrayer", videoId(links, AssemblyService.KEY_PRAYER));
        model.addAttribute("videoPledge", videoId(links, AssemblyService.KEY_PLEDGE));
        model.addAttribute("videoHindi",  videoId(links, AssemblyService.KEY_HINDI));

        return "assembly";
    }

    @PostMapping("/regenerate")
    public String regenerateThoughts() {
        thoughtConfigService.regeneratePool();
        return "redirect:/assembly";
    }

    private String videoId(Map<String, String> links, String key) {
        String url = links.get(key);
        return AssemblyService.extractVideoId(url);
    }
}

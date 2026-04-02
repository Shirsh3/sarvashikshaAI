package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.AssemblyVideoSlot;
import com.sarvashikshaai.service.AssemblyConfigService;
import com.sarvashikshaai.service.AssemblyService;
import com.sarvashikshaai.service.ThoughtConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/assembly")
@RequiredArgsConstructor
public class AssemblyController {

    private static final Map<String, String> SLOT_TITLES = Map.of(
            "anthem", "National Anthem",
            "pledge", "Pledge",
            "prayer", "Morning Prayer",
            "hindi", "Hindi Prayer"
    );

    private final AssemblyService assemblyService;
    private final ThoughtConfigService thoughtConfigService;
    private final AssemblyConfigService assemblyConfigService;

    @GetMapping
    public String assembly(Model model, HttpServletRequest request) {

        // ── Thought + Word + Habit: loaded async via GET /api/assembly/daily-thought ──

        // ── Static text (from JSON, used internally only) ────────────────────
        AssemblyService.AssemblyContent content = assemblyService.getAssemblyContent();
        model.addAttribute("anthem",        content.anthem().text());
        model.addAttribute("anthemMeaning", content.anthem().meaning());
        model.addAttribute("pledge",        content.pledge().text());
        model.addAttribute("prayer",        content.prayer().text());
        model.addAttribute("prayerMeaning", content.prayer().meaning());

        // ── YouTube links from config ────────────────────────────────────────
        Map<String, String> links = assemblyService.getAssemblyLinks(null);

        String vAnthem = videoId(links, AssemblyService.KEY_ANTHEM);
        String vPrayer = videoId(links, AssemblyService.KEY_PRAYER);
        String vPledge = videoId(links, AssemblyService.KEY_PLEDGE);
        String vHindi = videoId(links, AssemblyService.KEY_HINDI);

        model.addAttribute("videoAnthem", vAnthem);
        model.addAttribute("videoPrayer", vPrayer);
        model.addAttribute("videoPledge", vPledge);
        model.addAttribute("videoHindi", vHindi);

        var cfg = assemblyConfigService.getOrCreate();
        var order = assemblyConfigService.resolveSlotOrder(cfg.getSlotOrder());
        List<AssemblyVideoSlot> assemblySlots = buildSlots(vAnthem, vPledge, vPrayer, vHindi, order);
        model.addAttribute("assemblySlots", assemblySlots);
        model.addAttribute("assemblyFirstSlot", assemblySlots.isEmpty() ? null : assemblySlots.get(0));
        model.addAttribute("assemblyPlaylistCsv", String.join(",", assemblySlots.stream().map(AssemblyVideoSlot::videoId).toList()));

        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName() + ":" + request.getServerPort();
        }
        String origin = request.getScheme() + "://" + host;
        model.addAttribute("embedOrigin", origin);
        model.addAttribute("embedOriginEncoded", URLEncoder.encode(origin, StandardCharsets.UTF_8));

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

    private List<AssemblyVideoSlot> buildSlots(String vAnthem, String vPledge, String vPrayer, String vHindi, List<String> order) {
        Map<String, String> byKey = new HashMap<>();
        byKey.put("anthem", vAnthem);
        byKey.put("pledge", vPledge);
        byKey.put("prayer", vPrayer);
        byKey.put("hindi", vHindi);
        List<AssemblyVideoSlot> out = new ArrayList<>();
        for (String key : order) {
            String title = SLOT_TITLES.get(key);
            if (title == null) {
                continue;
            }
            addSlot(out, byKey.get(key), title, key);
        }
        return out;
    }

    private static void addSlot(List<AssemblyVideoSlot> list, String id, String title, String slotKey) {
        if (id == null || id.isBlank()) return;
        String safe = id.trim();
        if (!safe.matches("[a-zA-Z0-9_-]{11}")) return;
        list.add(new AssemblyVideoSlot(safe, title, null, slotKey));
    }
}

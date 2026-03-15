package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.entity.TeacherSettings;
import com.sarvashikshaai.repository.TeacherSettingsRepository;
import com.sarvashikshaai.service.GoogleSheetsService;
import com.sarvashikshaai.service.StudentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/teacher")
@RequiredArgsConstructor
@Slf4j
public class TeacherPanelController {

    private final GoogleSheetsService sheetsService;
    private final StudentSyncService  syncService;
    private final TeacherSettingsRepository settingsRepo;

    // ── /teacher → redirect to setup ─────────────────────────────────────────

    @GetMapping
    public String teacherHome() {
        return "redirect:/teacher/setup";
    }

    // ── Setup page (sheet picker) ─────────────────────────────────────────────

    @GetMapping("/setup")
    public String setupPage(
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client,
            Model model) {

        String email       = principal.getAttribute("email");
        String name        = principal.getAttribute("name");
        String accessToken = client.getAccessToken().getTokenValue();

        // Load teacher's Google Sheets list for the picker dropdown
        model.addAttribute("teacherName",  name);
        model.addAttribute("teacherEmail", email);
        model.addAttribute("sheets",       sheetsService.listSpreadsheets(accessToken));

        // Load existing settings (if teacher has already configured a sheet)
        settingsRepo.findById(email).ifPresent(s -> {
            model.addAttribute("settings", s);

            // If sheet is already configured, load students into cache if not already loaded
            if (s.getSheetId() != null && !syncService.isReady(email)) {
                syncService.resync(email, accessToken);
            }
        });

        model.addAttribute("students",     syncService.getStudents(email));
        model.addAttribute("assemblyCount", syncService.getAssemblyLinks(email).size());
        model.addAttribute("hindiCount",    syncService.getThoughts(email).hindi().size());
        model.addAttribute("englishCount",  syncService.getThoughts(email).english().size());
        return "teacher/setup";
    }

    // ── Save selected sheet + trigger sync ────────────────────────────────────

    @PostMapping("/sheet")
    public String saveSheet(
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client,
            @RequestParam String sheetId,
            @RequestParam String sheetName,
            RedirectAttributes redirectAttrs) {

        String email       = principal.getAttribute("email");
        String accessToken = client.getAccessToken().getTokenValue();

        syncService.syncStudents(email, accessToken, sheetId, sheetName);
        redirectAttrs.addFlashAttribute("syncMessage", buildSyncMessage(email));
        return "redirect:/teacher/setup";
    }

    // ── Manual sync ───────────────────────────────────────────────────────────

    @PostMapping("/sync")
    public String manualSync(
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client,
            RedirectAttributes redirectAttrs) {

        String email       = principal.getAttribute("email");
        String accessToken = client.getAccessToken().getTokenValue();

        syncService.resync(email, accessToken);
        redirectAttrs.addFlashAttribute("syncMessage", buildSyncMessage(email));
        return "redirect:/teacher/setup";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildSyncMessage(String email) {
        int students  = syncService.getStudents(email).size();
        int assembly  = syncService.getAssemblyLinks(email).size();
        int hindiQ    = syncService.getThoughts(email).hindi().size();
        int englishQ  = syncService.getThoughts(email).english().size();

        StringBuilder msg = new StringBuilder("Sync complete! ");
        msg.append(students).append(" students");
        msg.append(" · ").append(assembly).append(" assembly video").append(assembly == 1 ? "" : "s");
        msg.append(" · ").append(hindiQ).append(" Hindi + ").append(englishQ).append(" English thoughts loaded.");

        if (assembly == 0) msg.append(" (Add an 'Assembly' tab with Section | YouTube URL columns.)");
        if (hindiQ == 0 && englishQ == 0) msg.append(" (Add a 'Thoughts' tab with Language | Quote columns.)");

        return msg.toString();
    }

    // ── Students list page ────────────────────────────────────────────────────

    @GetMapping("/students")
    public String studentsPage(
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client,
            Model model) {

        String email       = principal.getAttribute("email");
        String accessToken = client.getAccessToken().getTokenValue();

        // Auto-load if cache is empty but sheet is configured
        if (!syncService.isReady(email)) {
            syncService.resync(email, accessToken);
        }

        model.addAttribute("teacherName", principal.getAttribute("name"));
        model.addAttribute("students",    syncService.getStudents(email));
        model.addAttribute("lastSync",    settingsRepo.findById(email)
                .map(TeacherSettings::getLastSync).orElse(null));

        return "teacher/students";
    }
}

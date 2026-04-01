package com.sarvashikshaai.controller;

import com.sarvashikshaai.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/leaderboard")
    public String leaderboard(
            Model model,
            @RequestParam(required = false, defaultValue = "month") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
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
        ZoneId zone = ZoneId.systemDefault();
        List<LeaderboardService.LeaderboardRow> rows =
                leaderboardService.buildLeaderboard(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
        model.addAttribute("leaderboardRows", rows);
        model.addAttribute("podiumFirst", rows.size() > 0 ? rows.get(0) : null);
        model.addAttribute("podiumSecond", rows.size() > 1 ? rows.get(1) : null);
        model.addAttribute("podiumThird", rows.size() > 2 ? rows.get(2) : null);
        model.addAttribute("restRows", rows.size() > 3 ? rows.subList(3, rows.size()) : List.of());
        model.addAttribute("filterRange", rangeValue);
        model.addAttribute("filterDateFrom", from);
        model.addAttribute("filterDateTo", to);
        return "leaderboard";
    }
}

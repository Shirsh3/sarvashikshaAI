package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.Student;
import com.sarvashikshaai.model.entity.AttendanceRecord;
import com.sarvashikshaai.repository.AttendanceRepository;
import com.sarvashikshaai.service.StudentListService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/attendance")
@RequiredArgsConstructor
@Slf4j
public class AttendanceController {

    private final StudentListService      studentListService;
    private final AttendanceRepository    attendanceRepo;

    // ── Page ─────────────────────────────────────────────────────────────────

    @GetMapping
    public String attendancePage(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        if (date == null) date = LocalDate.now();

        List<Student> students = studentListService.getStudents();
        LocalDate finalDate = date;
        Map<String, Boolean> markedMap = attendanceRepo.findByDateOrderByStudentNameAsc(date)
                .stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentName, AttendanceRecord::isPresent));

        model.addAttribute("students",    students);
        model.addAttribute("markedMap",   markedMap);
        model.addAttribute("currentDate", date);
        model.addAttribute("today",       LocalDate.now());

        long presentCount = markedMap.values().stream().filter(v -> v).count();
        long absentCount  = markedMap.values().stream().filter(v -> !v).count();
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("absentCount",  absentCount);
        model.addAttribute("unmarkedCount", students.size() - markedMap.size());

        return "attendance";
    }

    // ── Mark attendance AJAX ─────────────────────────────────────────────────

    @PostMapping("/mark")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> mark(
            @RequestBody MarkRequest req) {

        LocalDate date = req.date() != null ? req.date() : LocalDate.now();

        AttendanceRecord record = attendanceRepo.findByStudentNameAndDate(req.studentName(), date)
                .orElseGet(() -> new AttendanceRecord(req.studentName(), date, req.present()));
        record.setPresent(req.present());
        record.setMarkedAt(java.time.Instant.now());
        attendanceRepo.save(record);

        log.info("Attendance: {} → {} on {}", req.studentName(), req.present() ? "Present" : "Absent", date);
        return ResponseEntity.ok(Map.of("status", "ok", "studentName", req.studentName(), "present", req.present()));
    }

    // ── Export CSV ────────────────────────────────────────────────────────────

    @GetMapping("/export")
    public void exportCsv(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to",   required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletResponse response) throws IOException {

        if (from == null) from = LocalDate.now().minusMonths(1);
        if (to   == null) to   = LocalDate.now();

        List<AttendanceRecord> records = attendanceRepo.findByDateBetweenOrderByDateAscStudentNameAsc(from, to);

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"attendance_" + from + "_to_" + to + ".csv\"");
        response.setHeader("BOM", "\uFEFF"); // UTF-8 BOM for Excel

        PrintWriter writer = response.getWriter();
        writer.println("Date,Student Name,Status");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (AttendanceRecord r : records) {
            writer.println(r.getDate().format(fmt) + "," +
                    escapeCsv(r.getStudentName()) + "," +
                    (r.isPresent() ? "Present" : "Absent"));
        }
        writer.flush();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public record MarkRequest(String studentName, LocalDate date, boolean present) {}
}

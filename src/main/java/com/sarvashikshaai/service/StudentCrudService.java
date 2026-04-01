package com.sarvashikshaai.service;

import com.sarvashikshaai.model.Student;
import com.sarvashikshaai.model.entity.StudentEntity;
import com.sarvashikshaai.repository.StudentEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentCrudService {

    private final StudentEntityRepository studentRepo;

    public List<Student> getAllActive() {
        return studentRepo.findByActiveTrue().stream()
                .sorted((a, b) -> {
                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                    if (a.getUpdatedAt() == null) return 1;
                    if (b.getUpdatedAt() == null) return -1;
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                })
                .map(this::toStudent)
                .toList();
    }

    public List<StudentEntity> getAllEntities() {
        return studentRepo.findAll();
    }

    public Optional<Student> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return studentRepo.findById(name.trim()).map(this::toStudent);
    }

    @Transactional
    public void createOrUpdate(Student student) {
        if (student.getName() == null || student.getName().isBlank()) {
            throw new IllegalArgumentException("Student name is required");
        }
        if (student.getGrade() == null || student.getGrade().isBlank()) {
            throw new IllegalArgumentException("Grade is required");
        }
        String key = (student.getOriginalName() != null && !student.getOriginalName().isBlank())
                ? student.getOriginalName().trim()
                : student.getName().trim();

        // Auto-generate code if missing.
        String normalizedCode = normalizeStudentCode(student.getCode());
        if (normalizedCode == null || normalizedCode.isBlank()) {
            normalizedCode = generateNextStudentCode(key);
        }
        student.setCode(normalizedCode);

        if (student.getCode() != null && !student.getCode().isBlank()) {
            String code = student.getCode().trim();
            StudentEntity byCode = studentRepo.findByCode(code);
            if (byCode != null && !byCode.getName().equals(key)) {
                throw new IllegalArgumentException("Student code '" + code + "' is already in use.");
            }
        }
        StudentEntity entity = studentRepo.findById(key)
                .orElseGet(StudentEntity::new);
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        entity.setName(student.getName().trim());
        entity.setCode(student.getCode() != null ? student.getCode().trim() : null);
        entity.setGrade(student.getGrade() != null ? student.getGrade().trim() : null);
        entity.setStrength(student.getStrength());
        entity.setWeakness(student.getWeakness());
        entity.setNotes(student.getNotes());
        entity.setActive(student.isActive());
        studentRepo.save(entity);
    }

    private static String normalizeStudentCode(String raw) {
        if (raw == null) return null;
        String c = raw.trim();
        if (c.isBlank()) return null;
        // Standardize to lowercase "sNN" format when possible.
        c = c.replaceAll("\\s+", "");
        // If user typed S01/S001, normalize to lowercase s + digits as-is.
        if (c.matches("(?i)^s\\d+$")) {
            return "s" + c.substring(1);
        }
        return c;
    }

    private String generateNextStudentCode(String selfKeyName) {
        // Build a set of existing codes (lowercased) so we can pick the next available.
        Set<String> used = new HashSet<>();
        for (StudentEntity s : studentRepo.findAll()) {
            if (s == null) continue;
            String c = s.getCode();
            if (c == null) continue;
            c = c.trim();
            if (!c.isBlank()) used.add(c.toLowerCase());
        }

        // If the record exists and already has a code, keep it.
        if (selfKeyName != null && !selfKeyName.isBlank()) {
            StudentEntity existing = studentRepo.findById(selfKeyName.trim()).orElse(null);
            if (existing != null && existing.getCode() != null && !existing.getCode().trim().isBlank()) {
                return normalizeStudentCode(existing.getCode());
            }
        }

        for (int i = 1; i < 10_000; i++) {
            String code = (i < 100) ? String.format("s%02d", i) : String.format("s%03d", i);
            if (!used.contains(code)) return code;
        }
        // Fallback (should never happen)
        return "s" + System.currentTimeMillis();
    }

    @Transactional
    public void deleteByName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String key = name.trim();
        StudentEntity e = studentRepo.findById(key).orElse(null);
        if (e == null) return;
        // Soft-delete to avoid foreign-key failures (quiz/attendance/reading can reference student name).
        e.setActive(false);
        e.setUpdatedAt(Instant.now());
        studentRepo.save(e);
    }

    private Student toStudent(StudentEntity e) {
        Student s = new Student();
        s.setName(e.getName());
        s.setCode(e.getCode());
        s.setGrade(e.getGrade());
        s.setStrength(e.getStrength());
        s.setWeakness(e.getWeakness());
        s.setNotes(e.getNotes());
        s.setActive(e.isActive());
        s.setCreatedAt(e.getCreatedAt());
        s.setUpdatedAt(e.getUpdatedAt());
        return s;
    }
}


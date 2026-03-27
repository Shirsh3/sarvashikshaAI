package com.sarvashikshaai.service;

import com.sarvashikshaai.model.Student;
import com.sarvashikshaai.model.entity.StudentEntity;
import com.sarvashikshaai.repository.StudentEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
        if (student.getCode() != null && !student.getCode().isBlank()) {
            String code = student.getCode().trim();
            StudentEntity byCode = studentRepo.findByCode(code);
            String key = (student.getOriginalName() != null && !student.getOriginalName().isBlank())
                    ? student.getOriginalName().trim()
                    : student.getName().trim();
            if (byCode != null && !byCode.getName().equals(key)) {
                throw new IllegalArgumentException("Student code '" + code + "' is already in use.");
            }
        }
        String key = (student.getOriginalName() != null && !student.getOriginalName().isBlank())
                ? student.getOriginalName().trim()
                : student.getName().trim();
        StudentEntity entity = studentRepo.findById(key)
                .orElseGet(StudentEntity::new);
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        entity.setName(student.getName().trim());
        entity.setCode(student.getCode());
        entity.setGrade(student.getGrade());
        entity.setStrength(student.getStrength());
        entity.setWeakness(student.getWeakness());
        entity.setNotes(student.getNotes());
        entity.setActive(student.isActive());
        studentRepo.save(entity);
    }

    @Transactional
    public void deleteByName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        studentRepo.deleteById(name.trim());
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


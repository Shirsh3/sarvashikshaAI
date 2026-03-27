package com.sarvashikshaai.service;

import com.sarvashikshaai.model.Student;
import com.sarvashikshaai.model.entity.StudentEntity;
import com.sarvashikshaai.repository.StudentEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Provides student list from the database (StudentEntity).
 * Used when Sheet sync is not in use (M1: UI no longer uses Sheet; data from DB).
 */
@Service
@RequiredArgsConstructor
public class StudentListService {

    private final StudentEntityRepository studentRepo;

    /** Returns all active students from the database. */
    public List<Student> getStudents() {
        return StreamSupport.stream(studentRepo.findAll().spliterator(), false)
            .filter(StudentEntity::isActive)
            .map(this::toStudent)
            .toList();
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
        return s;
    }
}

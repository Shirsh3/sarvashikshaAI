package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.StudentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentReportRepository extends JpaRepository<StudentReport, Long> {
    Optional<StudentReport> findByStudentNameAndTermLabel(String studentName, String termLabel);
    List<StudentReport> findByStudentNameOrderByGeneratedAtDesc(String studentName);
}

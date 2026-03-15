package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.ReadingFeedbackRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReadingFeedbackRepository extends JpaRepository<ReadingFeedbackRecord, Long> {
    List<ReadingFeedbackRecord> findByStudentNameOrderByCreatedAtDesc(String studentName);
    List<ReadingFeedbackRecord> findByDateOrderByStudentNameAsc(LocalDate date);
}

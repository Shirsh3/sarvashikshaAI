package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.QuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface QuizRepository extends JpaRepository<QuizEntity, Long> {
    List<QuizEntity> findAllByOrderByCreatedAtDesc();
    List<QuizEntity> findByGradeOrderByCreatedAtDesc(String grade);

    @Query("SELECT COUNT(q) FROM QuizEntity q WHERE q.createdAt >= :from AND q.createdAt < :to AND (:grade IS NULL OR q.grade = :grade)")
    long countCreatedBetweenForGrade(@Param("from") Instant from, @Param("to") Instant to, @Param("grade") String grade);
}

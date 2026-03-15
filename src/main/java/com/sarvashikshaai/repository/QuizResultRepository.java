package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.QuizResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizResultRepository extends JpaRepository<QuizResultEntity, Long> {
    List<QuizResultEntity> findByStudentNameOrderByTakenAtDesc(String studentName);

    /** Native query to ensure all rows are returned (no JPA/cache quirks). */
    @Query(value = "SELECT * FROM quiz_results WHERE quiz_id = :quizId ORDER BY taken_at DESC", nativeQuery = true)
    List<QuizResultEntity> findAllByQuizIdOrderByTakenAtDesc(@Param("quizId") Long quizId);
}

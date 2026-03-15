package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.QuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizRepository extends JpaRepository<QuizEntity, Long> {
    List<QuizEntity> findAllByOrderByCreatedAtDesc();
    List<QuizEntity> findByGradeOrderByCreatedAtDesc(String grade);
}

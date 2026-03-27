package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.QuizQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestionEntity, Long> {
    List<QuizQuestionEntity> findByQuizIdOrderByQuestionOrderAsc(Long quizId);
    void deleteByQuizId(Long quizId);
}

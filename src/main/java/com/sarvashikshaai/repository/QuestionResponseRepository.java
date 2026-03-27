package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.QuestionResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionResponseRepository extends JpaRepository<QuestionResponseEntity, Long> {
    Optional<QuestionResponseEntity> findByQuestionId(Long questionId);
    List<QuestionResponseEntity> findByQuestion_QuizIdOrderByQuestion_QuestionOrderAsc(Long quizId);
    void deleteByQuestion_QuizId(Long quizId);
}

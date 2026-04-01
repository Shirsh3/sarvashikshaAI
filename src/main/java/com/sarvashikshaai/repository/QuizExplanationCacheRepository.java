package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.QuizExplanationCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizExplanationCacheRepository extends JpaRepository<QuizExplanationCacheEntity, Long> {
    Optional<QuizExplanationCacheEntity> findByQuestionId(Long questionId);
}


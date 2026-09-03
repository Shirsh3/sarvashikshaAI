package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.LearningMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningMessageRepository extends JpaRepository<LearningMessageEntity, Long> {

    List<LearningMessageEntity> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<LearningMessageEntity> findTop20ByConversationIdOrderByCreatedAtDesc(Long conversationId);
}

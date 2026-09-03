package com.sarvashikshaai.repository;

import com.sarvashikshaai.model.entity.LearningConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningConversationRepository extends JpaRepository<LearningConversationEntity, Long> {

    Optional<LearningConversationEntity> findFirstByTeacherUsernameAndGradeAndSubjectAndMaterialIdAndActiveTrueOrderByUpdatedAtDesc(
            String teacherUsername,
            String grade,
            String subject,
            Long materialId
    );

    Optional<LearningConversationEntity> findByIdAndTeacherUsername(Long id, String teacherUsername);
}

package com.sarvashikshaai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "learning_conversations")
@Getter
@Setter
@NoArgsConstructor
public class LearningConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_username", nullable = false, length = 120)
    private String teacherUsername;

    @Column(length = 16)
    private String grade;

    @Column(length = 80)
    private String subject;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "chapter_title", length = 300)
    private String chapterTitle;

    @Column(nullable = false, length = 16)
    private String language = "auto";

    @Column(name = "explanation_level", nullable = false, length = 32)
    private String explanationLevel = "simple";

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

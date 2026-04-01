package com.sarvashikshaai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "quiz_explanation_cache",
        uniqueConstraints = @UniqueConstraint(name = "uq_quiz_explain_question", columnNames = {"question_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class QuizExplanationCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "explanation", length = 8000)
    private String explanation;

    @Column(name = "explanation_section", length = 4000)
    private String explanationSection;

    @Column(name = "example_section", length = 4000)
    private String exampleSection;

    @Column(name = "key_point_section", length = 2000)
    private String keyPointSection;

    @Column(name = "video_id", length = 80)
    private String videoId;

    @Column(name = "wiki_gif_url", length = 800)
    private String wikiGifUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}


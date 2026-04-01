package com.sarvashikshaai.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A quiz/exam paper created by the teacher.
 * Questions are stored in quiz_questions table (relational model).
 */
@Entity
@Table(name = "quizzes")
@Getter @Setter @NoArgsConstructor
public class QuizEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "subject", length = 100)
    private String subject;

    @Column(name = "topic", length = 200)
    private String topic;

    @Column(name = "grade", length = 50)
    private String grade;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "question_count")
    private int questionCount;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "is_locked", nullable = false)
    private boolean isLocked = false;

    /** Optional OpenAI-generated decorative image URL; reused as watermark for all questions when set. */
    @Column(name = "cover_image_url", length = 1024)
    private String coverImageUrl;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizQuestionEntity> questions = new ArrayList<>();

    public QuizEntity(String title, String subject, String grade, int questionCount) {
        this.title         = title;
        this.subject       = subject;
        this.topic         = subject;
        this.grade         = grade;
        this.description   = "";
        this.questionCount = questionCount;
        this.createdAt     = Instant.now();
    }

    public QuizEntity(String title, String subject, String topic, String grade, String description, int questionCount) {
        this.title = title;
        this.subject = subject;
        this.topic = topic;
        this.grade = grade;
        this.description = description;
        this.questionCount = questionCount;
        this.createdAt = Instant.now();
    }
}

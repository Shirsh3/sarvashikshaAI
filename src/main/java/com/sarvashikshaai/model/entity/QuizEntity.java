package com.sarvashikshaai.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A quiz/exam paper created by the teacher.
 * Questions are stored as a JSON string for flexibility
 * (avoids a separate QuizQuestion table for this MVP).
 *
 * JSON format for questionsJson:
 * [
 *   {"type":"MCQ",   "text":"...", "options":["A","B","C","D"], "answer":"B"},
 *   {"type":"TF",    "text":"...", "answer":"True"},
 *   {"type":"SHORT", "text":"...", "answer":"..."}
 * ]
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

    @Column(name = "grade", length = 50)
    private String grade;

    @Column(name = "questions_json", nullable = false, length = 8000)
    private String questionsJson;

    @Column(name = "question_count")
    private int questionCount;

    @Column(name = "created_at")
    private Instant createdAt;

    public QuizEntity(String title, String subject, String grade, String questionsJson, int questionCount) {
        this.title         = title;
        this.subject       = subject;
        this.grade         = grade;
        this.questionsJson = questionsJson;
        this.questionCount = questionCount;
        this.createdAt     = Instant.now();
    }
}

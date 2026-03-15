package com.sarvashikshaai.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One student's completed quiz attempt.
 * answersJson: [{"questionIndex":0,"given":"B","correct":"B","isRight":true}, ...]
 */
@Entity
@Table(name = "quiz_results")
@Getter @Setter @NoArgsConstructor
public class QuizResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "quiz_title", length = 300)
    private String quizTitle;

    @Column(name = "student_name", nullable = false, length = 200)
    private String studentName;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "answers_json", length = 4000)
    private String answersJson;

    @Column(name = "taken_at")
    private Instant takenAt;

    public QuizResultEntity(Long quizId, String quizTitle, String studentName,
                             int score, int totalQuestions, String answersJson) {
        this.quizId         = quizId;
        this.quizTitle      = quizTitle;
        this.studentName    = studentName;
        this.score          = score;
        this.totalQuestions = totalQuestions;
        this.answersJson    = answersJson;
        this.takenAt        = Instant.now();
    }
}

package com.sarvashikshaai.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "questions")
@Getter @Setter @NoArgsConstructor
public class QuizQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", insertable = false, updatable = false)
    private QuizEntity quiz;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType;

    @Column(name = "question_text", nullable = false, length = 6000)
    private String questionText;

    @Column(name = "option_a", length = 2000)
    private String optionA;

    @Column(name = "option_b", length = 2000)
    private String optionB;

    @Column(name = "option_c", length = 2000)
    private String optionC;

    @Column(name = "option_d", length = 2000)
    private String optionD;

    @Column(name = "correct_answer", nullable = false, length = 2000)
    private String correctAnswer;

    /** Short teaching note (from AI) for printable "explanations" PDF. */
    @Column(name = "answer_explanation", length = 6000)
    private String answerExplanation;

    @Column(name = "marks", nullable = false)
    private Integer marks = 1;

    /** Optional CDN URL (e.g. Lorem Picsum) for a subtle background watermark on the take-quiz card. */
    @Column(name = "watermark_image_url", length = 1024)
    private String watermarkImageUrl;
}

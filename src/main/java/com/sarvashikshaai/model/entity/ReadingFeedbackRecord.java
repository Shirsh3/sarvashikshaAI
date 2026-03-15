package com.sarvashikshaai.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Stores one reading practice session in H2.
 * Captures rich analytics so the teacher can track student progress over time.
 *
 * Analytics columns available for reporting:
 *   fluencyScore       – overall smoothness (1-10)
 *   pronunciationScore – clarity of word pronunciation (1-10)
 *   paceScore          – reading speed appropriateness (1-10)
 *   accuracyScore      – word-for-word accuracy vs original (1-10)
 *   confidenceScore    – AI-judged confidence/hesitation (1-10)
 *   originalWordCount  – word count of the article excerpt
 *   spokenWordCount    – word count of what the student said
 *   accuracyPercent    – calculated overlap percentage (0-100)
 *   difficultWords     – CSV of words the student struggled with
 *   goodWords          – CSV of words pronounced well
 */
@Entity
@Table(name = "reading_feedback")
@Getter @Setter @NoArgsConstructor
public class ReadingFeedbackRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_name", nullable = false, length = 200)
    private String studentName;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "article_title", length = 500)
    private String articleTitle;

    // ── Core scores (1-10); Integer so existing DB NULLs are allowed ───────

    @Column(name = "fluency_score")
    private Integer fluencyScore;

    @Column(name = "pronunciation_score")
    private Integer pronunciationScore;

    @Column(name = "pace_score")
    private Integer paceScore;

    @Column(name = "accuracy_score")
    private Integer accuracyScore;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    // ── Word count analytics; Integer so existing DB NULLs are allowed ──────

    @Column(name = "original_word_count")
    private Integer originalWordCount;

    @Column(name = "spoken_word_count")
    private Integer spokenWordCount;

    /** Percentage of original words correctly spoken (0–100). Nullable for existing DB rows. */
    @Column(name = "accuracy_percent")
    private Integer accuracyPercent;

    // ── Qualitative feedback ───────────────────────────────────────────────

    @Column(name = "hindi_feedback", length = 2000)
    private String hindiFeedback;

    @Column(name = "english_feedback", length = 2000)
    private String englishFeedback;

    @Column(name = "comprehension_question", length = 500)
    private String comprehensionQuestion;

    /** Comma-separated words the student struggled with. */
    @Column(name = "difficult_words", length = 500)
    private String difficultWords;

    /** Comma-separated words pronounced particularly well. */
    @Column(name = "good_words", length = 500)
    private String goodWords;

    /** One-line coaching tip for next session. */
    @Column(name = "improvement_tip", length = 500)
    private String improvementTip;

    @Column(name = "created_at")
    private Instant createdAt;

    public ReadingFeedbackRecord(String studentName, String articleTitle,
                                  int fluencyScore, int pronunciationScore,
                                  int paceScore, int accuracyScore, int confidenceScore,
                                  int originalWordCount, int spokenWordCount, int accuracyPercent,
                                  String hindiFeedback, String englishFeedback,
                                  String comprehensionQuestion,
                                  String difficultWords, String goodWords, String improvementTip) {
        this.studentName         = studentName;
        this.articleTitle        = articleTitle;
        this.fluencyScore        = fluencyScore;
        this.pronunciationScore  = pronunciationScore;
        this.paceScore           = paceScore;
        this.accuracyScore       = accuracyScore;
        this.confidenceScore     = confidenceScore;
        this.originalWordCount   = originalWordCount;
        this.spokenWordCount     = spokenWordCount;
        this.accuracyPercent     = accuracyPercent;
        this.hindiFeedback       = hindiFeedback;
        this.englishFeedback     = englishFeedback;
        this.comprehensionQuestion = comprehensionQuestion;
        this.difficultWords      = difficultWords;
        this.goodWords           = goodWords;
        this.improvementTip      = improvementTip;
        this.date                = LocalDate.now();
        this.createdAt           = Instant.now();
    }
}

package com.sarvashikshaai.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Cached AI-generated report card per student per term.
 * Generated ONCE via OpenAI from aggregated H2 data; re-viewed at zero cost.
 */
@Entity
@Table(name = "student_reports",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_name", "term_label"}))
@Getter @Setter @NoArgsConstructor
public class StudentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_name", nullable = false, length = 200)
    private String studentName;

    @Column(name = "term_label", nullable = false, length = 50)
    private String termLabel;

    // Aggregated stats (stored so teacher can see numbers without parsing text)
    @Column(name = "attendance_percent")
    private int attendancePercent;

    @Column(name = "avg_fluency")
    private double avgFluency;

    @Column(name = "avg_accuracy")
    private double avgAccuracy;

    @Column(name = "avg_quiz_score")
    private double avgQuizScore;

    @Column(name = "reading_sessions")
    private int readingSessions;

    @Column(name = "difficult_words", length = 500)
    private String difficultWords;

    // AI-generated narrative (Hindi + English, returned as one block)
    @Column(name = "report_text", length = 6000)
    private String reportText;

    @Column(name = "generated_at")
    private Instant generatedAt;

    public StudentReport(String studentName, String termLabel,
                          int attendancePercent, double avgFluency, double avgAccuracy,
                          double avgQuizScore, int readingSessions, String difficultWords,
                          String reportText) {
        this.studentName       = studentName;
        this.termLabel         = termLabel;
        this.attendancePercent = attendancePercent;
        this.avgFluency        = avgFluency;
        this.avgAccuracy       = avgAccuracy;
        this.avgQuizScore      = avgQuizScore;
        this.readingSessions   = readingSessions;
        this.difficultWords    = difficultWords;
        this.reportText        = reportText;
        this.generatedAt       = Instant.now();
    }
}

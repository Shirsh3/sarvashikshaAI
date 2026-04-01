package com.sarvashikshaai.model.dto;

/**
 * Minimal metrics needed to generate teacher insights (rule-based, no AI calls).
 */
public record StudentMetrics(
        int attendancePercentage,
        int quizScore,
        ReadingLevel readingLevel
) {
    public StudentMetrics {
        // Keep values in a sane 0..100 range (helps avoid weird UI output).
        if (attendancePercentage < 0) attendancePercentage = 0;
        else if (attendancePercentage > 100) attendancePercentage = 100;

        if (quizScore < 0) quizScore = 0;
        else if (quizScore > 100) quizScore = 100;

        if (readingLevel == null) {
            throw new IllegalArgumentException("readingLevel must not be null");
        }
    }
}


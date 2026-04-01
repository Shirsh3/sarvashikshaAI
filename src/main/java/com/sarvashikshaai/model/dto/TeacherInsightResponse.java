package com.sarvashikshaai.model.dto;

/**
 * Output for teacher insight generation.
 */
public record TeacherInsightResponse(
        String insightEnglish,
        String insightHindi
) {
    public TeacherInsightResponse {
        if (insightEnglish == null || insightHindi == null) {
            throw new IllegalArgumentException("insights must not be null");
        }
    }
}


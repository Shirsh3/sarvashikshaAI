package com.sarvashikshaai.model.dto;

/**
 * Response for streak calculations.
 */
public record StreakResponse(
        int currentStreak,
        StreakLevel level,
        String message
) {
    public StreakResponse {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
    }
}


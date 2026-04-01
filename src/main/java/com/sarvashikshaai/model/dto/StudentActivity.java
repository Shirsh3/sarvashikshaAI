package com.sarvashikshaai.model.dto;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents one student activity on a specific date.
 * Used for streak calculations across QUIZ / READING / ATTENDANCE.
 */
public record StudentActivity(
        String studentId,
        LocalDate activityDate,
        Type type
) {
    public StudentActivity {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("studentId must not be blank");
        }
        Objects.requireNonNull(activityDate, "activityDate must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    public enum Type {
        QUIZ,
        READING,
        ATTENDANCE
    }
}


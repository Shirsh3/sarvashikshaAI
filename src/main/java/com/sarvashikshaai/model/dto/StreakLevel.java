package com.sarvashikshaai.model.dto;

public enum StreakLevel {
    NONE,           // 0
    STARTED,        // 1–2
    CONSISTENT,     // 3–6
    STRONG,         // 7–14
    CHAMPION;       // 15+

    public static StreakLevel getStreakLevel(int streak) {
        if (streak <= 0) return NONE;
        if (streak <= 2) return STARTED;
        if (streak <= 6) return CONSISTENT;
        if (streak <= 14) return STRONG;
        return CHAMPION;
    }
}


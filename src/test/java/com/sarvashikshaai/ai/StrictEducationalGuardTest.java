package com.sarvashikshaai.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictEducationalGuardTest {

    @Test
    void blocksCelebrityFigureAsks() {
        assertTrue(StrictEducationalGuard.isBlocked("Explain aishwarya rai figurre"));
        assertTrue(StrictEducationalGuard.isBlocked("Explain Aishwarya Rai figure"));
        assertTrue(StrictEducationalGuard.isBlocked("Rate Indian actresses"));
    }

    @Test
    void allowsSchoolHistoryTopics() {
        assertFalse(StrictEducationalGuard.isBlocked("What is Quit India Movement"));
        assertFalse(StrictEducationalGuard.isBlocked("Explain the Renaissance in simple Hindi"));
        assertFalse(StrictEducationalGuard.isBlocked("What is a geometric figure in math"));
    }
}

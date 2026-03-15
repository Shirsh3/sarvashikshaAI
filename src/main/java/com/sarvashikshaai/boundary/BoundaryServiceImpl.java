package com.sarvashikshaai.boundary;

import org.springframework.stereotype.Service;

@Service
public class BoundaryServiceImpl implements BoundaryService {

    @Override
    public ExplanationBoundary getBoundaryForGrade(int grade) {
        int safeGrade = Math.min(12, Math.max(1, grade));

        if (safeGrade <= 3) {
            return new ExplanationBoundary(
                    3,
                    "Use very simple words suitable for young children.",
                    true
            );
        } else if (safeGrade <= 6) {
            return new ExplanationBoundary(
                    5,
                    "Use simple, clear language with a real-life example.",
                    true
            );
        } else if (safeGrade <= 9) {
            return new ExplanationBoundary(
                    6,
                    "Explain the concept clearly and allow basic reasoning.",
                    false
            );
        } else {
            return new ExplanationBoundary(
                    7,
                    "Give a short but more detailed explanation without going too deep.",
                    false
            );
        }
    }
}


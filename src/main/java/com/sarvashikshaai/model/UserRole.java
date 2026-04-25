package com.sarvashikshaai.model;

public enum UserRole {
    TEACHER,
    ADMIN,
    SUPER_ADMIN,
    /** Quiz-bank / PDF handout only — access to {@code /quiz/**} only, not the rest of teacher module */
    QUIZ
}


# Feature: Quiz Module

## Purpose

Create, manage, assign, and evaluate quizzes for classroom learning.

## Main Routes

- `GET /quiz/teacher` -> teacher quiz dashboard
- `POST /quiz/create` -> save quiz and questions
- `POST /quiz/ai-generate` -> AI question generation
- `DELETE /quiz/{id}` -> delete quiz
- `GET /quiz/take/{id}` -> student/teacher take quiz view
- `POST /quiz/{quizId}/lock` -> lock quiz
- `POST /quiz/questions/{questionId}/assign` -> assign student to question
- `POST /quiz/questions/{questionId}/answer` -> submit answer
- `GET /quiz/{quizId}/question-results` -> fetch response result rows

## AI Generation Inputs

- topic, grade, description
- optional source URL extraction
- optional file upload (PDF/image)
- count/language/difficulty (with constraints)

## Validation and Safety

- Quiz generation is education-scoped via query classification.
- Upload size limit enforced (`5 MB`).
- Handles extraction failures with safe fallback messages.

## Persistence Model

- Quiz metadata -> `quizzes`
- Question bank per quiz -> `questions`
- Student answers and scoring -> `question_responses`

## User Outcome

Teachers can rapidly generate MCQ papers, run quiz sessions, collect answers, and inspect results.


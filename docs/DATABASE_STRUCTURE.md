# SarvashikshaAI Database Structure

This document describes the current database schema inferred from JPA entities and Flyway migrations.

## Overview

Core domains stored in DB:
- authentication and roles
- role-based menu configuration
- student master data
- quiz/question/response pipeline
- attendance
- reading feedback analytics
- assembly and thought configuration
- grade reference data

## Tables

## `app_users`

Stores login accounts.

- `username` (PK, varchar 120)
- `password_hash` (varchar 200, not null)
- `role` (varchar 30, not null; values include `TEACHER`, `ADMIN`, `SUPER_ADMIN`)
- `active` (boolean, not null)
- `created_at` (timestamp, not null)

Source:
- Flyway `V1__create_app_users.sql`
- Entity: `AppUserEntity`

## `menu_items`

Stores sidebar/navigation configuration by role.

- `id` (PK, bigint identity)
- `menu_key` (unique, varchar 80, not null)
- `label` (varchar 120, not null)
- `href` (varchar 250, not null)
- `icon` (varchar 60)
- `sort_order` (int, not null)
- `enabled_teacher` (boolean, not null)
- `enabled_admin` (boolean, not null)

Source:
- Flyway `V2__create_menu_items.sql`
- Entity: `MenuItemEntity`

## `students`

Master student directory (persisted from teacher operations/sync).

- `name` (PK, varchar 200)
- `code` (unique, varchar 50)
- `grade` (varchar)
- `strength` (varchar 500)
- `weakness` (varchar 500)
- `notes` (varchar 1000)
- `active` (boolean)
- `created_at` (timestamp)
- `updated_at` (timestamp)
- `last_synced_at` (timestamp)

Entity: `StudentEntity`

## `attendance`

Daily attendance marks.

- `id` (PK, bigint identity)
- `student_name` (varchar 200, not null)
- `date` (date, not null)
- `present` (boolean, not null)
- `marked_at` (timestamp)

Constraints:
- unique (`student_name`, `date`)

Entity: `AttendanceRecord`

## `quizzes`

Quiz/exam metadata.

- `id` (PK, bigint identity)
- `title` (varchar 300, not null)
- `subject` (varchar 100)
- `topic` (varchar 200)
- `grade` (varchar 50)
- `description` (varchar 2000)
- `question_count` (int)
- `created_at` (timestamp)
- `is_locked` (boolean, not null)
- `cover_image_url` (varchar 1024)

Entity: `QuizEntity`

## `questions`

Questions belonging to quizzes.

- `id` (PK, bigint identity)
- `quiz_id` (bigint, not null)
- `question_order` (int, not null)
- `question_type` (varchar 20, not null)
- `question_text` (varchar 2000, not null)
- `option_a` (varchar 1000)
- `option_b` (varchar 1000)
- `option_c` (varchar 1000)
- `option_d` (varchar 1000)
- `correct_answer` (varchar 500, not null)
- `marks` (int, not null)
- `watermark_image_url` (varchar 1024)

Entity: `QuizQuestionEntity`

## `question_responses`

Stores per-question student assignment and answer result.

- `id` (PK, bigint identity)
- `question_id` (bigint, not null)
- `student_id` (varchar 50, not null; mapped to `students.code`)
- `answer` (varchar 4000)
- `is_correct` (boolean)
- `marks_awarded` (int)
- `answered_at` (timestamp)

Constraints:
- unique (`question_id`) via `uq_question_responses_question`

Entity: `QuestionResponseEntity`

## `reading_feedback`

Stores one reading evaluation session with score analytics.

- `id` (PK, bigint identity)
- `student_name` (varchar 200, not null)
- `date` (date, not null)
- `article_title` (varchar 500)
- `fluency_score` (int)
- `pronunciation_score` (int)
- `pace_score` (int)
- `accuracy_score` (int)
- `confidence_score` (int)
- `original_word_count` (int)
- `spoken_word_count` (int)
- `accuracy_percent` (int)
- `hindi_feedback` (varchar 2000)
- `english_feedback` (varchar 2000)
- `comprehension_question` (varchar 500)
- `difficult_words` (varchar 500)
- `good_words` (varchar 500)
- `improvement_tip` (varchar 500)
- `created_at` (timestamp)

Entity: `ReadingFeedbackRecord`

## `assembly_config`

Assembly media URL configuration.

- `id` (PK, bigint identity)
- `anthem_url` (varchar 500)
- `prayer_url` (varchar 500)
- `pledge_url` (varchar 500)
- `hindi_prayer_url` (varchar 500)

Entity: `AssemblyConfig`

## `thought_entries`

Thought-of-the-day pool.

- `id` (PK, bigint identity)
- `language` (varchar 5)
- `thought_hi` (varchar 1000)
- `thought_en` (varchar 1000)
- `word_english` (varchar 255)
- `word_hindi` (varchar 255)
- `habit` (varchar 1000)
- `shown` (boolean)

Entity: `ThoughtEntry`

## `teacher_settings`

Teacher sync settings.

- `email` (PK, varchar 255)
- `sheet_id` (varchar)
- `sheet_name` (varchar)
- `last_sync` (timestamp)

Entity: `TeacherSettings`

## `grade_ref`

Reference table for canonical grades (KG, UKG, 1-12).

- `code` (PK, varchar 16)
- `label` (varchar 64, not null)
- `sort_order` (int, not null)

Entity: `GradeRef`

## Relationship Notes

- `questions.quiz_id` -> logical FK to `quizzes.id` (mapped by JPA relation).
- `question_responses.question_id` -> logical FK to `questions.id` (mapped by JPA relation).
- `question_responses.student_id` -> logical FK to `students.code` (mapped by JPA relation).
- `attendance.student_name` links by student name (string reference, not strict FK).
- `reading_feedback.student_name` links by student name (string reference, not strict FK).

## Migration Notes

- Flyway currently contains explicit migrations for:
  - `app_users`
  - `menu_items`
- Other tables are currently created/managed through JPA/Hibernate entity mappings in runtime environments.


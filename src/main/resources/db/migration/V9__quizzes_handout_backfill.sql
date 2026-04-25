-- Ensure existing rows are non-null (Hibernate may have added a nullable column first)
UPDATE quizzes SET handout_only = FALSE WHERE handout_only IS NULL;
ALTER TABLE quizzes ALTER COLUMN handout_only SET DEFAULT FALSE;
ALTER TABLE quizzes ALTER COLUMN handout_only SET NOT NULL;

-- One row per (question, student) so multiple students can answer the same question
-- and the leaderboard can aggregate across all students.

ALTER TABLE question_responses DROP CONSTRAINT IF EXISTS uq_question_responses_question;

-- Hibernate / older DBs may have used a different unique constraint name on question_id alone.
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.conname
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    WHERE t.relname = 'question_responses'
      AND c.contype = 'u'
      AND array_length(c.conkey, 1) = 1
      AND EXISTS (
        SELECT 1 FROM pg_attribute a
        WHERE a.attrelid = c.conrelid AND a.attnum = c.conkey[1] AND a.attname = 'question_id'
      )
  LOOP
    EXECUTE format('ALTER TABLE question_responses DROP CONSTRAINT %I', r.conname);
  END LOOP;
END $$;

ALTER TABLE question_responses
    ADD CONSTRAINT uq_question_responses_question_student UNIQUE (question_id, student_id);

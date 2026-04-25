-- Increase question text limits for AI/NCERT generation (avoid truncation in app code)
ALTER TABLE questions
    ALTER COLUMN correct_answer TYPE VARCHAR(2000);

ALTER TABLE questions
    ALTER COLUMN question_text TYPE VARCHAR(6000);

ALTER TABLE questions
    ALTER COLUMN option_a TYPE VARCHAR(2000);

ALTER TABLE questions
    ALTER COLUMN option_b TYPE VARCHAR(2000);

ALTER TABLE questions
    ALTER COLUMN option_c TYPE VARCHAR(2000);

ALTER TABLE questions
    ALTER COLUMN option_d TYPE VARCHAR(2000);

ALTER TABLE questions
    ALTER COLUMN answer_explanation TYPE VARCHAR(6000);


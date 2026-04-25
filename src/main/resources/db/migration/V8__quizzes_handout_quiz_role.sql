-- PDF-handout quizzes: not intended for in-app "Start" (live) session
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS handout_only BOOLEAN NOT NULL DEFAULT FALSE;

-- App user role: dedicated quiz-bank account (see AppUserRoleConstraintFixer for runtime sync)

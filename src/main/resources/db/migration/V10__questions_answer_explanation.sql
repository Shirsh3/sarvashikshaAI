-- Optional teaching note for PDF "explanations" export (from AI for quiz-bank handouts)
ALTER TABLE questions ADD COLUMN IF NOT EXISTS answer_explanation VARCHAR(2000);

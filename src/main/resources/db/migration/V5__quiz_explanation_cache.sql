CREATE TABLE IF NOT EXISTS quiz_explanation_cache (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    explanation VARCHAR(8000),
    explanation_section VARCHAR(4000),
    example_section VARCHAR(4000),
    key_point_section VARCHAR(2000),
    video_id VARCHAR(80),
    wiki_gif_url VARCHAR(800),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_quiz_explain_question UNIQUE (question_id)
);


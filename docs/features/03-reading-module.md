# Feature: Reading Module

## Purpose

Support guided reading practice with AI evaluation and persistent analytics per student session.

## Main Routes

- `GET /reading` -> reading page
- `POST /reading/generate-passage` -> generate or normalize class passage
- `POST /reading/feedback` -> evaluate student reading and save session result

## Key Behavior

### Passage Generation

- Teacher provides prompt (and optional grade).
- `ReadingEvaluationService` returns title + passage text.
- UI uses generated text for class reading mode.

### Reading Evaluation

- Request includes student speech text and context passage.
- Service returns score breakdown and feedback:
  - fluency
  - pronunciation
  - pace
  - accuracy
  - confidence
  - difficult words
  - good words
  - comprehension prompt
  - improvement tip

### Persistence

Each feedback call is stored in `reading_feedback` table (`ReadingFeedbackRecord`) with scores and analytics columns.

## User Outcome

Teachers get immediate AI feedback plus long-term historical records usable in dashboards/insights.


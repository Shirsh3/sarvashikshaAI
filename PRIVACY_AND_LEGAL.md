# Privacy and Legal — SarvashikshaAI

This document summarizes how the app handles data from a privacy and legal perspective (India / typical NGO use).

---

## 1. Data we store (and where)

| Data | Where stored | Purpose |
|------|----------------|--------|
| **Student name, grade, strength, weakness, notes** | H2 DB (`students` table) + in-memory cache | Synced from teacher’s Google Sheet; used for attendance, reading, quiz, report cards. |
| **Teacher email** | H2 DB (`teacher_settings`), OAuth table (`principal_name`) | Identify teacher, link to their sheet. |
| **OAuth tokens** (Google) | H2 DB (`oauth2_authorized_client`) | Keep teacher logged in; access Sheets/Drive. |
| **Reading feedback** (scores, feedback text, no student name in AI prompt) | H2 DB (`reading_feedback`) | Per-session reading analytics; student identified by `student_name` in DB only. |
| **Attendance** (student_name, date, present/absent) | H2 DB (`attendance`) | Daily attendance. |
| **Quiz results** (student_name, score, answers) | H2 DB (`quiz_results`) | Quiz attempts and scores. |
| **Report cards** (student_name, term, report text) | H2 DB (`student_reports`) | Cached AI-generated reports; report text does **not** contain student names (per design). |
| **Teacher assistant logs** (prompt summary, output) | H2 DB (`teacher_assistant_log`) | Last 10 generated items; no student names. |

All of the above stays on your server (H2 file and in-memory). No student or teacher PII is sent to third parties except as below.

---

## 2. Data sent to third parties

### OpenAI (API)

- **Classroom “Explain”**: Only the **question/topic** is sent. No student or teacher identifiers.
- **Reading evaluation**: **Article title, original text, spoken text** are sent. **Student name is not sent** (removed by design).
- **Report card generation**: **Aggregated stats** (attendance %, scores, trends, difficult words) are sent. **Student name is not sent**; model is told to refer to “this student”.
- **Quiz question generation**: **Topic or extracted text** from file. No student names.
- **Teacher assistant**: **Teacher’s prompt and optional file content**. No student names.

So: **no student or teacher names (or other PII) are sent to the AI model.**

### Google

- **OAuth**: Used so the teacher can sign in and grant access to their **own** Sheets/Drive.
- **Sheets API**: Teacher’s spreadsheet (students, thoughts, assembly links) is read using their token. Data is then stored in H2; Google is not used as the primary store for app runtime.
- **Drive API**: Used only to **list** spreadsheet files (metadata) so the teacher can pick a sheet. No student data is sent to Drive beyond what’s in the sheet the teacher chose.

### Other

- **ZenQuotes**: Only a request for a random quote; no PII.
- **Wikipedia / YouTube**: Only topic/search terms; no PII.
- **RSS (The Hindu)**: Fetched by the server; no PII sent.

---

## 3. Logging

- **Student name** appears in a few server logs (e.g. “Saved reading feedback for ‘X’”, “Attendance: X → Present”). Use for operational/audit only; ensure log files and access are restricted.
- **Teacher email** can appear in logs (e.g. “Syncing students for email@… from sheet”). Same as above.
- No logging of OAuth tokens, API keys, or full content of prompts/responses.

Recommendation: In production, restrict log file access and retention, and consider redacting names if you need to share logs.

---

## 4. Security (relevant for privacy)

- **Teacher routes** (`/teacher/**`) require Google login; students and classroom devices use public routes (no login).
- **CSRF** is enabled for the app; certain JSON/fetch endpoints are excluded so they can be called from the front end; those endpoints do not expose PII beyond what’s needed for the feature.
- **H2 console** is enabled and reachable; it can show all DB data (including student names). **Recommendation**: Disable or protect it in production (e.g. separate auth or turn off).
- **Secrets**: API keys and OAuth client secret must not be committed. Use environment variables (see `application.properties` and comments) and keep production config outside the repo.

---

## 5. Legal / consent (summary)

- **Students**: The app is used in a school/NGO context. Ensure the organisation has a lawful basis to process student data (e.g. consent or legitimate interest) and that a simple notice/consent (e.g. from parents/school) is in place for: attendance, reading practice, quizzes, and report cards.
- **Teachers**: They sign in with Google and choose their sheet; that constitutes consent for reading sheet data and storing it in H2 as described above.
- **Data retention**: H2 and logs retain data until you delete it or the files. Define retention (e.g. how long to keep attendance, reports, logs) and implement deletion if required.
- **India**: If you are subject to DPDP or other Indian data protection rules, treat student names and any other identifiers as personal data; the above (no PII to OpenAI, PII only in your DB and logs, controlled access) supports compliance, but you should align with your legal advisor and policy.

---

## 6. Checklist

- [x] Student names not sent to OpenAI (reading, report cards).
- [x] Teacher/student names not included in AI prompts; report text is name-free.
- [x] Student data stored only in your H2 and (via sync) in the teacher’s Google Sheet.
- [ ] **You**: Use env vars for API keys and client secret; do not commit them. For local dev, set `OPENAI_API_KEY`, `YOUTUBE_DATA_KEY`, and `GOOGLE_OAUTH_CLIENT_SECRET` in your environment (or use a separate `application-local.properties` and add it to `.gitignore`).
- [ ] **You**: In production, disable or secure H2 console and restrict log access.
- [ ] **You**: Have a lawful basis and notice/consent for processing student data (e.g. school/NGO policy).

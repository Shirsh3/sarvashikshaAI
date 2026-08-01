# Personal assistant + Alexa

Endpoints on `dev` (Render HTTPS):

| Method | Path | Use |
|--------|------|-----|
| GET | `/api/v1/health` | Liveness |
| POST | `/api/v1/ask` | `{"question":"...","locale":"en-IN"}` → `{"answer":"..."}` |
| POST | `/alexa` | Alexa Custom Skill endpoint |

Alexa Developer Console → Endpoint HTTPS → `https://<your-render-host>/alexa`

Intent: `AskTeacherIntent` with slot `question` (`AMAZON.SearchQuery`).

Requires `OPENAI_API_KEY` on the host (already used by the app).

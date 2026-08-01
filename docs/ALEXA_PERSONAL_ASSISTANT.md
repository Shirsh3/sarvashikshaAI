# Personal assistant + Alexa (with security)

## Endpoints

| Method | Path | Security |
|--------|------|----------|
| GET | `/api/v1/health` | Open (Render health) |
| POST | `/api/v1/ask` | API key when `assistant.security.api-key` is set |
| POST | `/alexa` | Alexa skill id when `assistant.security.alexa-skill-id` is set |

## Why two mechanisms?

Alexa’s skill runtime **cannot** send your custom `X-Api-Key`. It only posts Amazon’s request JSON (+ signature headers).

So:

1. **`/alexa`** — lock to your skill via **Skill ID** (`amzn1.ask.skill....`)
2. **`/api/v1/ask`** — lock with a **shared API key** (for curl / apps / Lambda proxy)

## Render env vars

| Variable | Example | Purpose |
|----------|---------|---------|
| `ASSISTANT_SECURITY_ENABLED` | `true` | Master switch |
| `ASSISTANT_SECURITY_API_KEY` | long random string | Protects `/api/v1/ask` |
| `ASSISTANT_SECURITY_ALEXA_SKILL_ID` | `amzn1.ask.skill.xxxx` | Protects `/alexa` |
| `OPENAI_API_KEY` | `sk-...` | OpenAI (already used) |

Generate an API key locally:

```bash
openssl rand -hex 32
```

Skill ID: Alexa Developer Console → your skill → **Endpoint** / skill info → Application Id.

## Call examples

Ask (with key):

```bash
curl -sS -X POST https://<host>/api/v1/ask \
  -H 'Content-Type: application/json' \
  -H "X-Api-Key: $ASSISTANT_SECURITY_API_KEY" \
  -d '{"question":"What is photosynthesis?","locale":"en-IN"}'
```

Or: `Authorization: Bearer <same-key>`

Alexa skill endpoint URL (no API key header):

`https://<host>/alexa`

## Alexa console setup

1. Custom skill → invocation name
2. Intent `AskTeacherIntent`, slot `question` (`AMAZON.SearchQuery`)
3. Endpoint HTTPS → `https://<host>/alexa`
4. Copy skill id into `ASSISTANT_SECURITY_ALEXA_SKILL_ID` on Render → redeploy

## Next hardening (optional)

Amazon also recommends verifying **Signature** + **SignatureCertChainUrl** on every `/alexa` request so forged posts are rejected even if someone guesses the skill id. Skill-id check alone is a good MVP; add signature verification before public launch.

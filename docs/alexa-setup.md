# Connect Sarvasiksha AI to Alexa

Personal assistant skill wiring. Endpoints:

| Endpoint | Use |
|----------|-----|
| `POST /api/v1/ask` | Simple JSON for any client |
| `POST /alexa` | Alexa Skill Kit request/response format |

Use your **Render HTTPS** URL (or local tunnel for dev).

## 1. Deploy / run API

Render: set `OPENAI_API_KEY`, deploy this repo, confirm:

`GET https://<your-render-host>/api/v1/health` → `openaiConfigured: true`

Local: `./scripts/run-local.sh /path/to/.crds` then tunnel if needed.

Skill endpoint: `https://<your-render-host>/alexa`

## 2. Create the skill (Alexa Developer Console)

1. Go to [Alexa Developer Console](https://developer.amazon.com/alexa/console/ask) → **Create Skill**.
2. Name: `Sarvasiksha AI`, model: **Custom**, host: **Provision your own**.
3. **Invocation name**: e.g. `sarva`.
4. **Interaction model** → add intent:

**Intent name:** `AskTeacherIntent`

**Sample utterances:**

```
ask {question}
what is {question}
explain {question}
tell me about {question}
{question}
```

**Slot:** `question` — type `AMAZON.SearchQuery` (or custom slot type).

5. Also enable built-ins: `AMAZON.HelpIntent`, `AMAZON.StopIntent`, `AMAZON.CancelIntent`.
6. **Endpoint** → HTTPS → paste `https://<your-render-host>/alexa` → save → **Build model**.
7. Test in the **Test** tab: `open sarva` → ask any question.

## 3. Production notes

- Add Alexa **request signature verification** before wide use (not in this MVP).
- Keep `OPENAI_API_KEY` only on the server (Render env).

## Manual smoke test (no Alexa device)

```bash
curl -sS -X POST http://localhost:8090/alexa \
  -H 'Content-Type: application/json' \
  -d '{
    "version":"1.0",
    "request":{
      "type":"IntentRequest",
      "locale":"en-IN",
      "intent":{
        "name":"AskTeacherIntent",
        "slots":{"question":{"name":"question","value":"What is 2 plus 2?"}}
      }
    }
  }'
```

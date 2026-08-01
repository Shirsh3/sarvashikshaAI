# Alexa smoothness checklist (console + server)

Server (`dev`) now supports: session chat memory, Repeat, Fallback handling, signature verification (env), concise prompts, better errors, welcome/reprompt.

## Add these intents in Alexa Console

**Build → Intents → Add** (built-in):

1. **AMAZON.FallbackIntent** — already often present; keep it  
2. **AMAZON.RepeatIntent** — add if missing  

Then **Save Model → Build skill**.

### How to use
- Follow-up: stay in session, say `ask …` again  
- Repeat: `repeat that` / `Alexa, repeat`  
- Fallback: unknown phrases get a nudge to say `ask …`  
  (Alexa usually does **not** send the raw unknown text to the skill)

## Render env (security)

```text
ASSISTANT_SECURITY_ENABLED=true
ASSISTANT_SECURITY_API_KEY=...
ASSISTANT_SECURITY_ALEXA_SKILL_ID=amzn1.ask.skill....
```

`POST /alexa` **always** verifies Amazon signatures (no flag). Curl without Alexa headers will fail — use the Alexa Test console / device.

## Optional next (later)
- SSML pauses  
- Rate limiting  
- Privacy policy page before publish  
- Metrics / structured logging of session.new + latency 

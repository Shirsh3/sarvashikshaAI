# SarvashikshaAI

SarvashikshaAI is a local Spring Boot + Thymeleaf web application that helps NGO teachers explain topics to students (grades 1–12) using a projector.  
Teachers enter a topic and grade; the system calls OpenAI (`gpt-4o-mini`) to generate a child-friendly explanation.

## Tech stack

- Java 17+
- Spring Boot (web, thymeleaf, validation)
- Maven
- OpenAI API (via HTTP using `WebClient`)

## Running locally

The repo uses **placeholder** config (no secrets committed). Provide secrets in one of these ways:

1. **Option A — Local properties file (recommended)**  
   Copy `src/main/resources/application-local.properties.example` to `src/main/resources/application-local.properties`, fill in your keys, then run with the `local` profile:

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

   `application-local.properties` is gitignored; never commit it.

2. **Option B — Environment variables**  
   Set `OPENAI_API_KEY`, `YOUTUBE_DATAKEY`, and the Google OAuth vars (see `application-prod.properties` or [docs/DEPLOY_RAILWAY.md](docs/DEPLOY_RAILWAY.md)), then:

   ```bash
   mvn spring-boot:run
   ```

3. **Run the application** from the project root (if using Option B, no profile needed):

   ```bash
   mvn spring-boot:run
   ```

3. **Open the UI** in your browser:

   - Go to: `http://localhost:8080`

4. **Use in the classroom**

   - Type a topic (e.g. “What is a fraction?”).
   - Select the student grade (1–12).
   - Choose one of:
     - **Explain Topic**
     - **Give Example**
     - **Simplify Explanation**
   - The explanation appears on the right in large, projector-friendly text.

## Profiles

- **default** — Placeholders only; use with `application-local.properties` or env vars.
- **prod** — For production (e.g. Railway); all secrets from environment variables. Activate with `spring.profiles.active=prod`.
- **local** — Loads `application-local.properties` (gitignored) for local dev keys.

## Security

- No secrets are committed. Use `application-local.properties` (gitignored) or environment variables. In production (Railway), set variables in the Railway dashboard.


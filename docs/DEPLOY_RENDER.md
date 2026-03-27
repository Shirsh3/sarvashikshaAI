# Deploy SarvashikshaAI on Render (free tier + custom domain)

This guide walks you through deploying the app on [Render](https://render.com) with the **free tier**, **custom domain** (sarvashiksaai.in), and **prod profile**. No credit card required.

---

## 1. Prerequisites: code on GitHub

Your repo must be on GitHub (e.g. [Shirsh3/sarvashikshaAI](https://github.com/Shirsh3/sarvashikshaAI)). Push your branch if needed:

```bash
git push -u origin dev
# or
git push -u origin master
```

Do not commit secrets; you will add them as **Environment** variables in Render.

---

## 2. Create a Render account and Web Service

1. Go to [render.com](https://render.com) and sign in with **GitHub**.
2. In the [Dashboard](https://dashboard.render.com/), click **New +** → **Web Service**.
3. **Connect** your GitHub account if prompted, then select the repository **sarvashikshaAI** (or your fork).
4. Configure the service:

   | Field | Value |
   |-------|--------|
   | **Name** | `sarvashikshaai` (or any name; used in the `.onrender.com` URL) |
   | **Region** | Choose closest to your users (e.g. Oregon, Frankfurt) |
   | **Branch** | `dev` or `master` (the branch you want to deploy) |
   | **Runtime** | **Docker** |
   | **Instance Type** | **Free** |

5. Leave **Build Command** and **Start Command** empty when using Docker; Render will use your `Dockerfile`.
6. Click **Advanced** and add environment variables (Step 3 below) before or after first deploy.
7. Click **Create Web Service**. Render will build from your Dockerfile and deploy.

---

## 3. Environment variables (secrets)

Render injects these as environment variables; the app uses the **prod** profile and reads them.

**Local file vs environment variable**

| Local file (`application-local.properties`) | Environment variable (Render / Railway) | Description |
|---------------------------------------------|----------------------------------------|-------------|
| `openai.api-key` | `OPENAI_API_KEY` | OpenAI API key |
| `youtube.dataKey` | `YOUTUBE_DATAKEY` | YouTube Data API v3 key |
| `spring...google.client-id` | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `spring...google.client-secret` | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| — | `SPRING_PROFILES_ACTIVE` | Set to `prod` in production only (not in local file) |
| — | `SPRING_DATASOURCE_URL` | **Required.** JDBC URL from Render Postgres (see Section 7) |
| — | `SPRING_DATASOURCE_USERNAME` | Postgres username (from Render Postgres) |
| — | `SPRING_DATASOURCE_PASSWORD` | Postgres password (from Render Postgres) |

1. In your service, open **Environment** in the left sidebar.
2. Click **Add Environment Variable** and add each of these (use **Secret** for sensitive values):

   | Key | Value | Notes |
   |-----|--------|--------|
   | `SPRING_PROFILES_ACTIVE` | `prod` | Required for production config. |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://...` | From Render Postgres (see Section 7). **Required.** |
   | `SPRING_DATASOURCE_USERNAME` | (from Postgres) | From Render Postgres dashboard. |
   | `SPRING_DATASOURCE_PASSWORD` | (from Postgres) | From Render Postgres dashboard (use **Secret**). |
   | `OPENAI_API_KEY` | Your OpenAI API key | `sk-proj-...` |
   | `YOUTUBE_DATAKEY` | Your YouTube Data API v3 key | From Google Cloud Console |
   | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | Your Google OAuth client ID | `...apps.googleusercontent.com` |
   | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | Your Google OAuth client secret | From Google Cloud Console |

3. Optional: `OPENAI_API_BASE_URL`, `OPENAI_MODEL` if you use non-default values.
4. **PORT** is set by Render (default `10000`); the app already uses `server.port=${PORT:8080}`, so no need to set it unless you want to override.
5. Save; Render will redeploy if you choose **Save and Deploy** (or redeploy manually from **Manual Deploy**).

**Google OAuth for production:**  
In [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials → your OAuth 2.0 Client:

- Add **Authorized redirect URI**: `https://sarvashiksaai.in/login/oauth2/code/google`
- After adding the custom domain in Step 5, you can also add the Render URL: `https://YOUR_SERVICE.onrender.com/login/oauth2/code/google`

---

## 4. First deploy and default URL

1. Wait for the first build and deploy to finish (Logs / Events tab).
2. Your app will be available at **https://YOUR_SERVICE.onrender.com** (e.g. `https://sarvashikshaai.onrender.com`).
3. If the deploy fails, check **Logs** for errors (e.g. missing env vars, port not bound). The app must listen on the port given by `PORT` (Render sets this automatically).

---

## 5. Add custom domain (sarvashiksaai.in)

1. In your service, go to **Settings** → **Custom Domains**.
2. Click **Add Custom Domain**.
3. Enter **sarvashiksaai.in** and (optionally) **www.sarvashiksaai.in**. Render will show the targets to use.
4. In your DNS provider, add the records (see **Namecheap** below if you use Namecheap).
5. Save DNS. Render will issue a free TLS certificate; wait for the domain to show as verified (green check).

### If you use Namecheap

1. Log in at [namecheap.com](https://www.namecheap.com) → **Domain List** → click **Manage** next to **sarvashiksaai.in**.
2. Open the **Advanced DNS** tab.
3. **Remove** any existing **AAAA** records for `@` and `www` (Render does not support IPv6).
4. **Root domain (sarvashiksaai.in):**
   - Remove any existing **A** record for `@`.
   - Click **Add New Record** → choose **A Record**.
   - **Host:** `@`  
   - **Value:** `216.24.57.1` (Render’s load balancer)  
   - **TTL:** 1 min (or Automatic).
5. **www subdomain (www.sarvashiksaai.in):**
   - Remove any existing **CNAME** or **URL Redirect** for `www`.
   - Click **Add New Record** → choose **CNAME Record**.
   - **Host:** `www`  
   - **Value:** your Render host (e.g. `sarvashikshaai.onrender.com`)  
   - **TTL:** 1 min (or Automatic).
6. Save. Changes can take a few minutes; use [dnschecker.org](https://dnschecker.org) to verify.

---

## 6. Free tier behavior

- **Spins down** after about **15 minutes** of no traffic. The next request will **wake** the service (may take 30–60 seconds).
- **750 hours** of compute per month on the free tier; usually enough for a small app.
- **Custom domain and TLS** are included on the free tier.
- For always-on or more resources, upgrade to a paid instance in **Settings** → **Instance Type**.

---

## 7. PostgreSQL database (required)

The app uses **PostgreSQL** for both local and production. On Render’s free tier the filesystem is **ephemeral**: data may be lost on redeploy or when the instance is recreated after spin-down.

On Render: add a Postgres database (Dashboard → New + → PostgreSQL), then in your Web Service Environment set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. For local dev, use PostgreSQL and set these in `application-local.properties` (see example file).

---

## 8. Redeploy and updates

- **Auto-deploy:** Pushes to the connected branch trigger a new build and deploy.
- **Manual deploy:** **Manual Deploy** → **Deploy latest commit** (or clear build cache and deploy if needed).

---

## Quick checklist

- [ ] Repo on GitHub, branch pushed.
- [ ] Render Web Service created, **Runtime = Docker**, **Instance Type = Free**.
- [ ] **PostgreSQL** database created on Render; Web Service env has `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- [ ] Environment variables set: `SPRING_PROFILES_ACTIVE=prod`, `OPENAI_API_KEY`, `YOUTUBE_DATAKEY`, Google OAuth client ID and secret.
- [ ] Google OAuth redirect URI includes `https://sarvashiksaai.in/login/oauth2/code/google` (and optionally the Render URL).
- [ ] Custom domain **sarvashiksaai.in** (or subdomain) added in Render; DNS CNAME/A record pointing to Render.
- [ ] Domain verified and HTTPS working.

For more: [Render Web Services](https://render.com/docs/web-services), [Render Custom Domains](https://render.com/docs/custom-domains), [Render Docker](https://render.com/docs/docker).

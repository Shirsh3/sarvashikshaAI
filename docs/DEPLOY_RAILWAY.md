# Deploy SarvashikshaAI on Railway with sarvashiksaai.in

This guide walks you through deploying the app on [Railway](https://railway.app), keeping secrets in Railway’s **Variables** (not in the repo), and using your domain **sarvashiksaai.in**.

---

## 1. Push your code to GitHub

**Why you don’t see branches:** Branches only appear on GitHub after you **push**. Your local `dev` and `master` exist only on your machine until you run `git push`.

1. **If you use the existing repo** [Shirsh3/sarvashikshaAI](https://github.com/Shirsh3/sarvashikshaAI) (remote name `upstream`):
   - From your project root, push your branches (you’ll be asked for GitHub credentials):
   ```bash
   git push -u upstream dev
   git push upstream master
   ```
   - Then you’ll see `dev` and `master` on GitHub.

2. **If you create a new repo** under your account:
   - Create the repo on GitHub (e.g. `sarvashikshaai`), then:
   ```bash
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
   git push -u origin dev
   git push origin master
   ```

3. The repo uses **placeholder** config only; add real secrets in Railway Variables (Step 4), not in the repo.

---

## 2. Create a Railway project and deploy from GitHub

1. Go to [railway.app](https://railway.app) and sign in (GitHub is easiest).
2. Click **New Project**.
3. Choose **Deploy from GitHub repo** and select your repository.
4. Railway will detect the Java/Maven app and start a build. If you have a `Dockerfile` in the repo, Railway will use it; otherwise it uses Nixpacks for Java.
5. Wait for the first deployment to finish. You can watch **View logs** in the service.

---

## 3. Generate a Railway domain (needed before custom domain)

1. Open your **service** (the one that was created from the repo).
2. Go to **Settings** → **Networking** → **Public Networking**.
3. Click **Generate Domain**. You’ll get a URL like `something.up.railway.app`.
4. Save this URL; you’ll need it for DNS in Step 6.

---

## 4. Add secrets (Variables) in Railway

Never put real keys in the repo. Use Railway **Variables** so they are kept secret and injected as environment variables.

1. In your service, open the **Variables** tab.
2. Set the production profile: **`SPRING_PROFILES_ACTIVE`** = **`prod`**.
3. Add each variable (name and value). Spring Boot will pick them up automatically:

   | Variable name | Description | Example / notes |
   |---------------|-------------|------------------|
   | `OPENAI_API_KEY` | OpenAI API key | `sk-proj-...` |
   | `YOUTUBE_DATAKEY` | YouTube Data API v3 key | From Google Cloud Console |
   | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | Google OAuth client ID | `...apps.googleusercontent.com` |
   | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | Google OAuth client secret | From Google Cloud Console |
   | `SPRING_PROFILES_ACTIVE` | Active Spring profile | Set to `prod` for production |

3. Optional: if you use different values for base URL or model, you can add:
   - `OPENAI_API_BASE_URL`
   - `OPENAI_MODEL`

4. **Google OAuth for production:**  
   In [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials → your OAuth 2.0 Client:
   - Add **Authorized redirect URI**: `https://sarvashiksaai.in/login/oauth2/code/google`  
   - After adding the custom domain in Step 5, you can also add the Railway domain, e.g. `https://YOUR_APP.up.railway.app/login/oauth2/code/google`.

5. Click **Add** or **Update** for each variable. Railway will redeploy when you change variables.

---

## 5. Add custom domain sarvashiksaai.in in Railway

1. In the same service, go to **Settings** → **Networking** → **Public Networking**.
2. Under **Custom Domains**, click **+ Custom Domain**.
3. Enter: **sarvashiksaai.in** (or **www.sarvashiksaai.in** if you prefer).
4. Railway will show a **CNAME target**, e.g. `xxxxx.up.railway.app`. Copy it.

---

## 6. Point your domain to Railway (DNS)

At the place where you manage DNS for **sarvashiksaai.in** (e.g. GoDaddy, Namecheap, Cloudflare):

**Option A – Use root domain (sarvashiksaai.in)**

- If your provider supports **CNAME flattening** or **ALIAS/ANAME** at the root:
  - Create a CNAME (or ALIAS/ANAME) record:
    - **Name/host:** `@` (or root/apex).
    - **Target/value:** the CNAME target Railway gave you (e.g. `xxxxx.up.railway.app`).
- If your provider does **not** support CNAME at root (e.g. some GoDaddy/Namecheap setups), use a subdomain (Option B) or move DNS to Cloudflare and use their CNAME flattening for the root.

**Option B – Use subdomain (e.g. app.sarvashiksaai.in)**

- **Name:** `app` (or `www`, etc.).
- **Type:** CNAME.
- **Target:** the Railway CNAME target (e.g. `xxxxx.up.railway.app`).

Save the DNS records. Propagation can take a few minutes up to 48 hours.

---

## 7. SSL and verification

- Railway will issue a free SSL certificate (Let’s Encrypt) for your custom domain.
- In Railway, wait until the domain shows a **green check** (verified). If it stays “pending”, double-check the CNAME (or ALIAS) at your DNS provider.
- If you use **Cloudflare** in front of Railway, set SSL/TLS to **Full** (not Full Strict) as per Railway’s docs.

---

## 8. Will Railway run on H2?

**Yes.** The app is configured to use an **H2 file database** by default. Railway will run the app as-is and use H2.

**But:** On Railway the filesystem is **ephemeral**. Data in H2 may be **lost on redeploy or restart**. So H2 is fine for trying the app; for production, expect no persistence unless you switch to a hosted DB.

- For production, add a **PostgreSQL** (or MySQL) database in Railway and set `spring.datasource.url` (and related variables) so the app uses that instead of H2.
- If you keep H2 for now, use it only for testing; data might not persist across redeploys.

---

## 9. Redeploy and check

- After changing **Variables**, Railway usually redeploys automatically.
- You can also trigger a deploy: **Deployments** → **Redeploy** (or push a new commit).
- Open:
  - Railway URL: `https://YOUR_APP.up.railway.app`
  - Custom domain: `https://sarvashiksaai.in` (or `https://app.sarvashiksaai.in`)

---

## Quick checklist

- [ ] Code on GitHub (no secrets in repo).
- [ ] Railway project created, deploy from GitHub.
- [ ] Railway domain generated (e.g. `*.up.railway.app`).
- [ ] All secrets added under **Variables** (OPENAI, YouTube, Google OAuth).
- [ ] Google OAuth redirect URI updated for `https://sarvashiksaai.in/...`.
- [ ] Custom domain **sarvashiksaai.in** added in Railway.
- [ ] DNS CNAME (or ALIAS) pointing to Railway’s target.
- [ ] Domain verified (green check) and HTTPS working.

For more: [Railway Spring Boot guide](https://docs.railway.app/guides/spring-boot), [Railway custom domains](https://docs.railway.app/networking/domains/working-with-domains).

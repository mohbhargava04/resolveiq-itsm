# Running and deploying ResolveIQ

Three ways to run the app, from simplest to a public URL:

| Option | Needs | Database | Good for |
|---|---|---|---|
| [1. Local development](#1-local-development) | Java 21, Node 22 | H2 file in `backend/data` | Writing code |
| [2. Docker Compose](#2-docker-compose) | Docker | PostgreSQL 16 in a container | A production-like run on one laptop |
| [3. Render (cloud)](#3-render-cloud) | A GitHub and Render account | Render PostgreSQL | A public link for the demo and evaluators |

Then: [Google sign-in setup](#google-sign-in-setup) · [Inbound e-mail](#inbound-e-mail-optional) ·
[Settings reference](#settings-reference) · [After deploying](#after-deploying-checklist)

---

## 1. Local development

```bash
cd backend
./mvnw spring-boot:run          # http://localhost:8080, demo data loaded on first start
```

For live frontend editing, run the Vite dev server as well:

```bash
cd frontend
npm install
npm run dev                     # http://localhost:5173, API calls are proxied to :8080
```

After changing the frontend, `npm run build:backend` rebuilds the copy that Spring Boot serves.
To start over with fresh demo data, stop the server and delete `backend/data`.

## 2. Docker Compose

```bash
docker compose up --build       # http://localhost:8080
```

The root `Dockerfile` builds the frontend and backend from source in three stages (Node → Maven →
small JRE image running as a non-root user), and Compose starts it next to PostgreSQL 16. Data is kept
in the `pgdata` volume; `docker compose down -v` wipes it.

## 3. Render (cloud)

`render.yaml` is a Render **Blueprint**: it describes the web app and its database, so Render can create
both in one go.

1. Push the repository to GitHub.
2. On [render.com](https://render.com): **New → Blueprint**, pick the repository, and approve. Render
   creates `resolveiq-db` (PostgreSQL) and `resolveiq` (the app, built from the `Dockerfile`).
   - The JWT signing key, data-encryption key and inbound-mail secret are **generated randomly by Render**
     and never appear in the repository.
   - Database host, name, user and password are wired in automatically.
3. The first build takes about 5–8 minutes. When it finishes, open the service's URL
   (e.g. `https://resolveiq.onrender.com`).
4. In the service's **Environment** tab set `ITSM_PUBLIC_URL` to that exact URL (no trailing slash) and
   save. Render redeploys. E-mail links and Google sign-in use this address.

**Free plan limits** (from [Render's docs](https://render.com/docs/free)):
- The app **sleeps after 15 minutes** without visitors; the next visit takes about a minute to wake it.
  Open the link a couple of minutes before a demo.
- A free database **expires 30 days after it is created**. Create the Blueprint within a month of the
  evaluation (for a 22 November deadline, any time after late October is fine).

## Google sign-in setup

Optional. Without it, the Google button is shown but disabled with an explanation, and passwords work as usual.

1. Go to [Google Cloud Console](https://console.cloud.google.com/) → create a project (e.g. "ResolveIQ").
2. **APIs & Services → OAuth consent screen**: user type *External*, app name *ResolveIQ*, your e-mail as
   support and developer contact. Scopes: `openid`, `email`, `profile` (the defaults). While the app is in
   *Testing*, add the Google accounts that will sign in as **test users**.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Web application**
   - Authorised redirect URIs — add one per place the app runs:
     - `http://localhost:8080/api/auth/oauth/google/callback`
     - `https://<your-render-url>/api/auth/oauth/google/callback`
4. Copy the **client ID** and **client secret** into:
   - Render: `ITSM_GOOGLE_CLIENT_ID` and `ITSM_GOOGLE_CLIENT_SECRET` in the Environment tab;
   - locally: the same names as environment variables before `./mvnw spring-boot:run`, or in a `.env`
     file next to `docker-compose.yml` (already git-ignored).
5. In the app, sign in as an admin → **Admin → Settings → Sign-in and access** and set the allowed e-mail
   domains to your organisation's (for a quick test, your own address's domain, e.g. `gmail.com`).
   Only those domains can sign in with Google; new people become *Employees* until an admin changes
   their role.

The redirect URI must match exactly, including `https` and no trailing slash; a mismatch shows Google's
`redirect_uri_mismatch` page.

## Inbound e-mail (optional)

Replies to notification e-mails can update incidents. A mail provider with an inbound webhook (e.g.
SendGrid Inbound Parse, Mailgun Routes, Postmark Inbound) posts each received message to:

```
POST https://<your-url>/api/inbound/email
X-Inbound-Secret: <value of ITSM_INBOUND_EMAIL_SECRET>
Content-Type: application/json

{ "from": "ava@company.com", "subject": "Re: [INC-1042] ...", "text": "..." }
```

Without a provider, **Admin → E-mail & SMS → Received e-mail → Simulate an incoming e-mail** processes a message exactly the same way from inside the app.

## Settings reference

All settings are environment variables; defaults are fine for local development.

| Variable | Default | What it does |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` = H2 file database and dev tools; `postgres` = PostgreSQL |
| `PORT` | `8080` | HTTP port (cloud hosts set this) |
| `ITSM_DB_URL` | — | Full JDBC URL, or instead: |
| `ITSM_DB_HOST` / `ITSM_DB_PORT` / `ITSM_DB_NAME` | `localhost` / `5432` / `resolveiq` | Database location |
| `ITSM_DB_USERNAME` / `ITSM_DB_PASSWORD` | `resolveiq` | Database login |
| `ITSM_PUBLIC_URL` | `http://localhost:8080` | Public address for e-mail links and Google sign-in |
| `ITSM_JWT_SECRET` | dev value | **Set in production.** Signs access tokens (32+ characters) |
| `ITSM_DATA_KEY` | dev value | **Set in production.** Encrypts two-factor secrets (32+ characters) |
| `ITSM_DEMO_MODE` | `true` | Demo accounts on the sign-in page. `false` also turns on the startup safety check |
| `ITSM_FORWARD_HEADERS` | `none` | `framework` behind a cloud proxy, so cookies are marked `Secure` |
| `ITSM_API_DOCS` | `true` | OpenAPI description (Swagger UI needs an admin unless dev tools are on) |
| `ITSM_EXPOSE_DEV_TOOLS` | `true` in dev, else `false` | Swagger UI and H2 console without sign-in |
| `ITSM_RATE_LIMIT` | `true` | Per-address request limits |
| `ITSM_GOOGLE_CLIENT_ID` / `_SECRET` | empty | Enables Google sign-in |
| `ITSM_INBOUND_EMAIL_SECRET` | empty | Enables the inbound e-mail webhook |
| `ITSM_SMTP_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | empty | Real e-mail sending; without it, e-mails go to the in-app outbox |
| `ITSM_SEED_ENABLED` | `true` | Load demo data into an empty database |
| `ITSM_RESET_DEMO` | `false` | Wipe and reload demo data on every start |

With `ITSM_DEMO_MODE=false`, the app **refuses to start** if the JWT secret or data key are still the
public development values, or if dev tools are exposed. See [SECURITY.md](SECURITY.md).

## After deploying: checklist

- [ ] `https://<url>/actuator/health` shows `{"status":"UP"}`.
- [ ] The sign-in page loads; a demo account signs in.
- [ ] `ITSM_PUBLIC_URL` is set to the real address.
- [ ] Admin → Security shows no warnings you didn't expect.
- [ ] (If using Google) Google sign-in works for an allowed domain and is refused for others.
- [ ] Admin → Settings: allowed domains and "require 2FA for privileged roles" are set as intended.

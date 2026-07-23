# ITCABS — Deployment & Secrets

What you need to put ITCABS in front of real people, and exactly where each secret goes.
**The repo is public — never commit a real secret.** Every secret below is injected as an
environment variable at the host; the code already reads them (defaults are dev placeholders).

---

## The two thresholds

### A) First real run — you driving both roles (no external accounts needed)
Backend on your laptop, app on an emulator/your phone. OTP codes are read from the backend
logs (dev mode), so no SMS gateway is required.

- Backend: `cd backend && docker compose up --build -d` (API on `:8081`).
- App: run from Android Studio. Base URL is `http://10.0.2.2:8081/` (emulator → host loopback).
  For a **physical phone**, change it to your laptop's LAN IP (e.g. `http://192.168.1.20:8081/`)
  and be on the same Wi-Fi. (It's in `app/src/main/java/com/itcabs/di/AppModule.kt` → `DEV_BASE_URL`.)
- Get OTP: after tapping "send", read the code from `docker logs backend-api-1 | grep OTP`.

**Zero external accounts. This is the validation run — do this first.**

### B) Real pilot — a 2nd person on their own phone, anywhere
Now the backend must be on the public internet and OTPs must actually be delivered by SMS.

---

## Backend environment variables

Set these in your host's secret store (Render/Railway/Fly dashboard, or `-e` on the container).
The code reads them via `application.yml`; the value after `:` is the **dev default** you must override.

| Env var | Required for | Dev default (override in prod!) | Notes |
|---------|--------------|--------------------------------|-------|
| `DB_URL` | any deploy | `jdbc:postgresql://localhost:5432/itcabs` | point at your managed Postgres |
| `DB_USER` | any deploy | `postgres` | |
| `DB_PASSWORD` | any deploy | `postgres` | **secret** |
| `JWT_SECRET` | any deploy | `dev-secret-change-me-…` | **secret**, ≥32 bytes, random. Rotating it logs everyone out. |

These are now env-wired (set at the host):

| Env var | Required for | Default | Notes |
|---------|--------------|---------|-------|
| `OTP_PROVIDER` | live SMS | `dev` (logs code) | set `msg91` in prod |
| `MSG91_AUTH_KEY` | live SMS | — | **secret**; from MSG91 dashboard |
| `MSG91_TEMPLATE_ID` | live SMS | approved `itcabs_otp` id | non-secret; already defaulted |
| `PUSH_PROVIDER` | push | `noop` | set `fcm` in prod |
| `FIREBASE_CREDENTIALS` | push | — | path to the service-account JSON (Render Secret File) |
| `PORT` | host | `8080` | most PaaS inject this; the app binds it automatically |

> Live SMS in India also needs **DLT registration** on the MSG91 sender/template — business
> paperwork, separate from the code.

## Backend hosting — Render + Supabase (recommended)

Chosen to match the existing TapByWisein stack. `render.yaml` (repo root) is a Blueprint that
deploys `backend/Dockerfile` with a `/actuator/health` check; secrets are entered in the dashboard.

**Postgres — Supabase**
1. New Supabase project. Copy the connection string from **Settings → Database → Session pooler**
   (use the *session* pooler or the direct connection, **not** the transaction pooler on :6543 —
   pgBouncer transaction mode breaks Hikari/Flyway prepared statements).
2. Form the JDBC URL: `jdbc:postgresql://<host>:5432/postgres?sslmode=require` (SSL is required).
3. That host/user/password become `DB_URL` / `DB_USER` / `DB_PASSWORD`. Flyway migrates on first boot.

**Backend — Render**
1. Render → New → **Blueprint**, point at this repo (it reads `render.yaml`).
2. Fill the `sync:false` env vars in the dashboard: `DB_URL/DB_USER/DB_PASSWORD`, a strong random
   `JWT_SECRET` (≥32 bytes), `MSG91_AUTH_KEY`.
3. Add a **Secret File** named `firebase-admin.json` (paste the service-account JSON) — Render mounts
   it at `/etc/secrets/firebase-admin.json`, which `FIREBASE_CREDENTIALS` already points to.
4. **Free** plan is fine for a pilot (spins down after ~15 min idle → a cold start on the next
   request; the app's WebSocket auto-reconnects when traffic resumes). Move to **starter** for
   always-on before real launch.
5. Deploy. Verify `https://<service>.onrender.com/actuator/health` → `UP` and `/docs` loads.

Railway / Fly.io / Cloud Run work too (same Dockerfile + env vars); only the dashboard differs.

## Android app for the hosted backend

The **release** build's URL is now build-time configurable (no hardcoded value):
```
./gradlew :app:assembleRelease -Pitcabs.baseUrl=https://<service>.onrender.com/
```
(or set the `ITCABS_BASE_URL` env var). Debug builds still use `http://10.0.2.2:8081/`.
Distribution: sideload `app-release.apk` for a few testers, or a Play internal-testing track.

## Secret-handling rules (applies to me too)

- I will **not** enter or hold your real secret values. You paste them into the host's env/secret
  store yourself; the code reads them from the environment.
- Never commit a real `JWT_SECRET`, DB password, or SMS key. `application.yml` keeps **placeholders**.
- If a secret is ever committed to this public repo, treat it as compromised and rotate it.

## Minimum checklist for pilot B

1. Supabase Postgres provisioned; `DB_URL` (+`?sslmode=require`) / `DB_USER` / `DB_PASSWORD` set.
2. Strong random `JWT_SECRET` set.
3. `OTP_PROVIDER=msg91` + `MSG91_AUTH_KEY` set (and DLT registered for India delivery).
4. `PUSH_PROVIDER=fcm` + the `firebase-admin.json` Secret File uploaded.
5. Backend deployed (Render Blueprint); `/actuator/health` = UP and `/docs` reachable.
6. Release APK built with `-Pitcabs.baseUrl=https://…`, distributed to testers.
7. Seed one admin: `UPDATE users SET is_admin=true WHERE phone='+91…';` (to verify drivers).

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

Not yet env-wired (small code changes needed before pilot B):
- **`itcabs.otp.dev-log-code`** is hardcoded `true` — in prod it must be `false` (stop logging
  codes) and a real SMS sender must be active. Make it `${OTP_DEV_LOG:false}` when wiring SMS.
- **SMS gateway** (e.g. MSG91): the `OtpSender` bean is a seam with a dev-log impl today. Add a
  second impl that calls the gateway, selected by profile/env; it will need e.g. `MSG91_AUTH_KEY`
  (**secret**) + a sender/template id. Until this exists, pilot B can't send OTPs to strangers.

## Backend hosting

The `backend/Dockerfile` builds a JDK-21 image; `backend/docker-compose.yml` is the local stack.
For a hosted deploy: push the image (or point the host at the Dockerfile) + attach a managed
Postgres, set the env vars above. Any of Render / Railway / Fly.io works; all offer managed Postgres.
Flyway migrates the schema automatically on first boot.

## Android app for a real backend

Before a hosted run, the app must target the deployed URL instead of the loopback dev URL:
- Today: `DEV_BASE_URL` is hardcoded in `AppModule.kt`.
- Do (small follow-up): move it to a `BuildConfig` field / product flavors so `debug` → dev and
  `release` → the hosted `https://…` URL. (Noted in `AppModule.kt`'s ponytail comment.)
- Distribution: sideload the `app-debug.apk` for a handful of testers, or ship to a Play internal
  testing track for a wider pilot.

## Secret-handling rules (applies to me too)

- I will **not** enter or hold your real secret values. You paste them into the host's env/secret
  store yourself; the code reads them from the environment.
- Never commit a real `JWT_SECRET`, DB password, or SMS key. `application.yml` keeps **placeholders**.
- If a secret is ever committed to this public repo, treat it as compromised and rotate it.

## Minimum checklist for pilot B

1. Managed Postgres provisioned; `DB_URL`/`DB_USER`/`DB_PASSWORD` set.
2. Strong random `JWT_SECRET` set.
3. SMS gateway impl added + its key set; `dev-log-code` off.
4. Backend deployed at a public `https://` URL; `/docs` reachable.
5. App pointed at that URL (release build), APK distributed.
6. Seed one admin: `UPDATE users SET is_admin=true WHERE phone='+91…';` (to verify drivers).

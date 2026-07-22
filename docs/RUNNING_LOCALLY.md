# Running ITCABS locally (backend + Android app)

How to run the full stack on one machine: the Spring Boot backend in Docker, the Android
app on an emulator, talking to each other. Written for this repo's exact setup.

## Prerequisites

- **JDK 21** (Spring Boot 3.3 isn't certified on 25; the Gradle wrapper is pinned to 8.9).
- **Docker Desktop** (runs Postgres + the API).
- **Android Studio** (Ladybug or newer) with an **emulator** (API 34 image, e.g. Pixel 7).
- Android SDK — already at `/opt/homebrew/share/android-commandlinetools`; `local.properties`
  points `sdk.dir` there. On a fresh clone, create `local.properties` with:
  `sdk.dir=/opt/homebrew/share/android-commandlinetools`

## 1. Start the backend

```bash
cd backend
docker compose up --build -d          # Postgres + API on http://localhost:8081
docker compose logs -f api            # watch logs (OTP codes print here in dev)
```

- Swagger UI: <http://localhost:8081/docs>
- Health check: `curl http://localhost:8081/docs` should return 200.
- Stop later with `docker compose down`.

## 2. Run the Android app

1. Open the repo root (`~/itcabs`) in Android Studio → let Gradle sync (uses JDK 21).
2. Start an emulator (Device Manager → a Pixel / API 34 device).
3. Run the **`app`** configuration (▶). It installs `com.itcabs` ("IT Cars") and launches the
   login screen.

**Networking:** the app calls the backend at `http://10.0.2.2:8081/` — that's the emulator's
alias for your host's `localhost`, wired in `app/src/main/java/com/itcabs/di/AppModule.kt`.
Cleartext HTTP is allowed in the manifest for dev. (A physical device instead of an emulator
needs your machine's LAN IP there, not `10.0.2.2`.)

## 3. Get OTP codes (dev)

No SMS is sent in dev — the code is **logged by the backend**. After tapping "Continue with
OTP", grab it from the API logs:

```bash
docker compose -f backend/docker-compose.yml logs api | grep "OTP for"
# e.g.  OTP for +919812345678 = 481920
```

Enter that 6-digit code on the OTP screen.

## 4. End-to-end walkthrough (the full claim loop)

The core loop needs a coordinator (posts work), a verified driver (claims it), and one admin
action (verifies the driver). Do it with two logins:

**A. Coordinator posts a job**
1. Sign in with a phone number, role **Coordinator**.
2. Create Job → office, shift, one or more pickup legs (pickup/drop/seats/fare) → **Publish**.

**B. Driver signs up + KYC**
3. Sign out / use a second phone number, role **Driver**. Complete **KYC** (vehicle + masked
   Aadhaar/RC — dev values are fine).
4. The driver is `PENDING` and **cannot claim yet** until an admin verifies them.

**C. Verify the driver (admin, one-time via SQL)**
There's no admin UI yet, and admin is gated by an `is_admin` flag. Promote a user and verify
the driver directly in Postgres:
```bash
# make some user an admin (use the driver's own id to keep it simple, or a separate account)
docker compose -f backend/docker-compose.yml exec db \
  psql -U postgres -d itcabs -c "UPDATE users SET is_admin=true WHERE phone='+91<admin-phone>';"

# then call the admin verify endpoint with that admin's access token, OR just flip KYC directly:
docker compose -f backend/docker-compose.yml exec db \
  psql -U postgres -d itcabs -c "UPDATE driver_profiles SET kyc_status='VERIFIED' WHERE user_id=<driver-id>;"
```
(The proper path is `POST /api/v1/admin/drivers/{id}/verify` as an admin — see Swagger.)

**D. Driver claims**
5. Back in the driver app, the coordinator's open legs appear in the feed → **Claim Trip**.
   First claim wins; a second driver claiming the same leg gets "Trip already taken".

## 5. Troubleshooting

| Symptom | Fix |
|---|---|
| Gradle sync fails on JVM version | Android Studio → Settings → Build Tools → Gradle → set **Gradle JDK = 21**. Don't use the brew `gradle` (9.x breaks AGP 8.5.2); always use the wrapper. |
| App shows network / connection errors | Backend up? `curl localhost:8081/docs`. Emulator uses `10.0.2.2`, not `localhost`. |
| "cleartext not permitted" | Already allowed via `usesCleartextTraffic` in the manifest (dev only). |
| OTP screen won't accept the code | Codes expire in 5 min; request a fresh one and re-check the logs. |
| Driver can't claim | Driver KYC must be `VERIFIED` (step C), and there must be an OPEN leg (step A). |
| Port 8081 already in use | Another process; stop it or change the compose port mapping. |

## Notes

- Tokens are persisted on-device; access tokens auto-refresh on 401 via the OkHttp
  `TokenAuthenticator`.
- This is a **dev** setup: cleartext HTTP, dev JWT/DB secrets (env-overridable), OTP logged
  not sent. None of that is for production.

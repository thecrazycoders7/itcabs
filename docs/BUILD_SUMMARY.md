# ITCABS — Build Summary (what exists today)

_Snapshot as of 2026-07-21. Companion to `HANDOFF.md` (live status) and the architecture
docs. This is the "what we've built" overview._

## 1. What ITCABS is

A production-grade transport-dispatch platform replacing a 20,000-member Telegram group used
by IT-company transport coordinators and vendor drivers in Hyderabad.

- **Coordinators** post a *job* = office + shift + one or more **legs** (pickup → drop, area,
  time window, vehicle type, fare, seats).
- **Drivers** have a verified KYC profile and **claim** a leg — one tap, **first-claim-wins**,
  no double-claim.

The value: structured posting + atomic claiming + verified drivers, replacing free-text broadcast.

## 2. Architecture at a glance

```
Android app (Kotlin/Compose, multi-module)  ──HTTPS/JSON──►  Backend (Kotlin/Spring Boot)  ──►  Postgres
  :app  :feature:*  :core:*  :data  :domain                   identity + dispatch contexts        (Flyway)
```

- **Backend**: modular monolith, package-by-bounded-context, JDBC (no JPA), Flyway migrations.
- **Android**: layered multi-module (ADR-0007) — pure `:domain`, `:data` + `:core:network`
  for the API, `:core:designsystem` for the theme, `:feature:*` for screens, `:app` hosts Hilt DI.
- Decisions are recorded as ADRs in `docs/adr/` (0001–0010).

## 3. Backend — milestone M1 (COMPLETE, verified)

Kotlin + Spring Boot 3.3 + Postgres. Runs via `docker compose` in `backend/` on `:8081`
(Swagger at `/docs`).

| Area | What's built |
|------|--------------|
| **Auth** | Phone OTP → JWT access + opaque refresh token; `POST /auth/refresh` exchanges it (30-day TTL). OTP delivery behind an `OtpSender` seam (dev-log now, SMS gateway drop-in later). |
| **Driver KYC** | Submit KYC (Aadhaar stored as token + masked value only — never raw, ADR-0006); admin verify. |
| **Admin RBAC** | `/admin/**` guarded by an `is_admin` flag; admins minted out-of-band only. |
| **Dispatch** | Post job + legs; open-leg feed (area/vehicle filter); **atomic first-claim-wins**; coordinator status workflow; per-trip rating. |

**The core invariant is proven three ways:** 50 concurrent claimers → exactly 1 winner (manual
+ Testcontainers test), and full HTTP E2E (login → post → claim → 409 double-claim → complete →
rate). The claim is one atomic SQL `UPDATE` with the verified-driver gate inside it.

### Verified this session (E2E against the live stack)
- `POST /auth/refresh`: 200 / 401 invalid / 400 blank; 30-day expiry enforced.
- Admin RBAC: 403 non-admin / 200 admin / 401 anon.

## 4. Android — milestone M2 (in progress: full spine built, compile-verified)

Re-platform off the old Firestore-direct app onto the backend API. Built as 7 Gradle modules:

| Module | Type | Contents | Verified |
|--------|------|----------|----------|
| `:domain` | pure JVM | `User`, `Session`, `Leg`, `AppResult<T>` (Err carries HTTP code), repository interfaces | unit tests |
| `:core:network` | pure JVM | `AuthApi` + `DispatchApi` (Retrofit), DTOs, `AuthInterceptor`, `NetworkFactory` | unit tests |
| `:data` | pure JVM | `AuthRepositoryImpl`, `DispatchRepositoryImpl`, DTO↔domain mappers, in-memory `TokenStore` | unit tests |
| `:core:designsystem` | Android lib | `ItCabsTheme` — colors/type/shapes from the Stitch design system | compiles |
| `:feature:auth` | Android lib | `AuthViewModel` (UDF) + `AuthScreen` (login + OTP), styled to the mockups | compiles |
| `:feature:dispatch` | Android lib | driver feed + **Claim Trip** (handles 409), coordinator create-job (dynamic legs) | compiles |
| `:app` | Android app | `@HiltAndroidApp`, Hilt DI (composition root), Compose nav | **builds to APK** |

### What's verified
- **JVM unit tests: 7, all green** (`./gradlew :core:network:test :data:test`) — repository mapping,
  token storage, **claim-lost = 409**, refresh-without-token = 401, Retrofit annotation contract.
- **Full app builds to a 16 MB APK** (`com.itcabs` v0.2 "IT Cars") — the entire Hilt object graph
  validates at compile time (Compose UI → ViewModel → repository → API → token store all link).

### Design system
Implemented from the user's Stitch export (`~/Downloads/stitch_it_cars_enterprise_mobility_dashboard/`):
brand "IT Cars", primary `#2563EB`, lavender `#faf8ff` canvas / white cards, Inter type scale,
8px buttons / 16px cards. `login_screen`, `otp_verification`, `driver_home_screen`, and
`create_job_screen` are implemented in Compose.

## 5. The honest ceiling

- **The Android app compiles and builds an installable APK, but has NOT been run** — this
  environment has the SDK but **no emulator**. Screens are compile-verified and the DI graph
  is proven; "does it look/behave right on a device" needs Android Studio + an emulator/phone,
  with the backend reachable at `10.0.2.2:8081`.
- Backend logic, by contrast, **is** runtime-verified (Docker + real Postgres).

## 6. What's NOT built yet

- **Android runtime run** (needs emulator); role-based home routing off `/auth/me` (currently a
  manual hub); driver "my trips", coordinator dashboard, job details, ratings, notifications screens.
- **Token persistence** — `TokenStore` is in-memory; encrypted DataStore is the follow-up.
- **Auto token refresh** on 401 (OkHttp `Authenticator`).
- **Offline cache** — Room in `:data` (ADR-0007) not yet added.
- **Backend**: real SMS gateway (seam ready), realtime (WebSocket+Redis), FCM push, payments,
  masked calling, PostGIS geo feed — all later milestones.
- **Inter font** not bundled (using system font at the right metrics).

## 7. How to build / run

```bash
# Backend (runtime-verified)
cd backend && docker compose up --build -d      # API on :8081, Swagger at /docs

# Android (compile only here; run in Android Studio + emulator)
./gradlew :app:assembleDebug                    # produces app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:network:test :data:test         # JVM unit tests
```

Build env: Android SDK at `/opt/homebrew/share/android-commandlinetools`; use `./gradlew`
(Gradle 8.9, pinned — Gradle 9 breaks AGP 8.5.2); build with JDK 21.

## 8. Repository

`github.com/thecrazycoders7/itcabs` — public. Dev secrets in `application.yml` are
env-overridable placeholders; inject real values via env in any real deployment.

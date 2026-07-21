# ITCABS — Handoff Brief (for a fresh Claude session)

You are picking up an in-progress project. Read this top to bottom before doing anything.
Everything you need is on disk; this brief is the map.

---

## 0. Current status (2026-07-21) — READ FIRST

**The app now RUNS and the core dispatch loop is proven end-to-end on an emulator.**
This supersedes the older "compile-only, never run" notes further down.

### What was done this session
1. **Ran the Android app on an emulator for the first time** and verified the full auth
   path: launch → IT Cars login → phone/OTP against the live backend → new DRIVER created
   in Postgres → role-routes to driver home → **token persistence** confirmed (force-kill +
   relaunch stays signed in).
2. **Built the coordinator dashboard** — the last missing piece of the loop:
   - `feature/dispatch/.../CoordinatorHomeScreen.kt` + `CoordinatorHomeViewModel.kt`
     (uses `myLegs()` → `GET /api/v1/legs/mine`). Lists posted legs with an OPEN/CLAIMED
     status pill + a Refresh button; an internal `showCreate` flag swaps in the existing
     `CreateJobScreen` (no nav lib). On publish it returns to the list and refreshes.
   - `MainActivity` now routes COORDINATOR → `CoordinatorHomeScreen` (was going straight
     into the create form). Post/feed/claim UI all already existed.
3. **Drove the whole loop on one emulator with two accounts** and proved it:
   - Coordinator posts a job → dashboard shows it **OPEN** (₹450, Gachibowli → DLF Cyber City).
   - Driver sees it in the feed → **Claim Trip** → "Trip claimed!", feed empties.
     DB: `legs.status=CLAIMED, claimed_by=1`, one `claims_audit` row (atomic first-claim-wins).
   - Coordinator dashboard refresh → pill flips **OPEN → CLAIMED**.

### How to run it here (Docker-free — Docker Desktop wouldn't start; disk was full)
- **Backend as a host process** (no docker): build the fat jar once with the copied wrapper —
  `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootJar` — then run:
  `SERVER_PORT=8081 DB_URL=jdbc:postgresql://localhost:5432/itcabs DB_USER=apple DB_PASSWORD=x
  JWT_SECRET=<32+ chars> java -jar backend/build/libs/itcabs-backend-0.1.0.jar`
  against **local Postgres 15** (db `itcabs`). Dev OTPs are printed to the log (`DevLogOtpSender`).
- **Emulator**: `system-images;android-34;default;arm64-v8a`, AVD `itcabs`, booted headless
  (`emulator -avd itcabs -no-window -gpu swiftshader_indirect`); drive via `adb`.
  App debug BASE_URL is `http://10.0.2.2:8081/`.

### Known gaps / caveats
- **Claim needs a VERIFIED driver.** The claim gate requires `driver_profiles.kyc_status='VERIFIED'`,
  but there's no driver-onboarding / admin-verify screen yet (admin portal deferred). For the
  loop test the profile row was seeded directly in the DB. Building onboarding/verify is future work.
- **Sign-out bug (open):** the shared TopAppBar "Sign out" (in `MainActivity`'s `RoleHome`,
  unchanged this session) does not respond to taps from the **coordinator** screen, though it
  works from the **driver** screen. Reproduced twice. Worked around with `adb shell pm clear
  com.itcabs` to switch accounts. Flagged as a separate task — root cause not yet found.

### Next plan (M2 UI, in priority order)
1. **Fix the sign-out bug** (small, real defect; blocks clean account switching).
2. **Driver "my trips" + job-detail** screens (`GET /legs/claimed`; designs `claimed_trip_screen`,
   `job_details`) — the driver side after a claim.
3. **Ratings** screen (`rating_screen`, `POST /legs/{id}/rating`).
4. **Driver onboarding / KYC** so a driver can become claimable in-app (removes the DB-seed hack).
5. **At-rest token encryption** (KeyStore/Tink) — prefs are app-private but not encrypted.
6. **Room offline cache** in `:data` — lowest urgency.
Realtime (WebSocket push so the coordinator dashboard updates without Refresh) stays an M3 item.

---

## 1. What ITCABS is

A production-grade transport-dispatch platform replacing a chaotic 20,000-member
Telegram group used by IT-company transport coordinators and vendor drivers in
Hyderabad to arrange employee shuttle rides.

- **Coordinators** (demand) post a *job requirement* = office + shift + one or more
  **legs** (pickup → drop, area, time window, vehicle type, fare, seats).
- **Drivers** (supply) have a verified KYC profile and **claim** a specific leg,
  one tap, **first-claim-wins** (no double-claim).

The core value is replacing free-text broadcast + manual "closed" replies with
structured posting, atomic claiming, verified drivers, and live status.

## 2. Standing directives (do not violate)

- **Android is the only client** being built now. iOS / Web / Admin come later.
- **No web client** unless an Admin Portal is explicitly requested. The web app in
  `prototype/` is **ARCHIVED** — reference only, never extend it.
- **Refactor before adding features.** We are in a refactor phase (M0–M3). No new
  product features until the backend owns the domain and Android is re-platformed.
- **Maintainability over speed.** Every major decision → an ADR in `docs/adr/`.
- Never add "Co-Authored-By: Claude" or "Generated with Claude Code" to commits/PRs.
- Git: repo not initialized yet by request — do not run git init / commit unless asked.

## 3. Repo layout (`~/itcabs`)

```
app/          Android app (Kotlin/Compose + Firebase) — the ORIGINAL MVP scaffold.
              Single module, Firestore-direct. Proved the workflow. Being superseded.
backend/      ITCABS backend (Kotlin + Spring Boot 3.3 + Postgres) — M1, the real product.
prototype/    ARCHIVED web demo (Node+HTML). Do not touch. See prototype/ARCHIVED.md.
docs/         Authoritative architecture docs (read these):
  README.md                     index + current status
  architecture/TECHNICAL_AUDIT.md
  architecture/PRODUCTION_ARCHITECTURE.md
  REFACTORING_ROADMAP.md
  DEVELOPMENT_ROADMAP.md        milestones M0–M8 with Definitions of Done
  adr/0001..0010-*.md           architecture decisions
  backlog/M0-deferred.md        Android-app fixes deferred by user choice
```

## 4. What is BUILT and its state

### Backend (`backend/`) — milestone M1 backbone: DONE + VERIFIED
Kotlin + Spring Boot 3.3, Postgres, JDBC (no JPA), package-by-bounded-context
(`identity`, `dispatch`, `shared`). Flyway migration `V1__init.sql`.

Implemented + working end-to-end over HTTP:
- **Auth** (`identity/`): phone OTP (dev: code is logged, no SMS yet) → JWT access
  token; refresh token issued + stored in `device_sessions`. Lightweight JWT filter
  (not full Spring Security yet).
- **Driver KYC** + **admin verify** (`identity/DriverController.kt`). Aadhaar is
  stored ONLY as token + masked value (never raw) — ADR-0006.
- **Dispatch** (`dispatch/`): post job+legs, open-leg feed (area/vehicle filter),
  **the atomic first-claim-wins claim**, coordinator status workflow, per-trip rating.
- OpenAPI/Swagger at `/docs`.

**The core invariant is PROVEN, twice:**
1. 50 concurrent OS-level psql clients raced one leg against real Postgres →
   exactly 1 won, 49 lost. (Manual proof, already run and passed.)
2. `backend/src/test/kotlin/.../ClaimConcurrencyTest.kt` — Testcontainers version,
   compiles clean, runs in CI. (Same scenario, automated.)
3. Full HTTP E2E passed: coordinator login → post job → 2 drivers KYC+verify →
   driver1 claim = 200 → driver2 claim same leg = **409 "leg already taken"** →
   complete → rate. All green.

The claim (heart of the system), in `dispatch/DispatchService.kt`:
```sql
UPDATE legs SET status='CLAIMED', claimed_by=:d, claimed_at=now(), version=version+1
 WHERE id=:id AND status='OPEN'
   AND EXISTS (verified, active driver :d)   -- gate is INSIDE the atomic update
RETURNING id;   -- 0 rows => lost the race => 409
```

### Android app (`app/`) — original MVP scaffold, NOT re-platformed yet
Kotlin/Compose, single module, talks directly to Firestore/Auth/Storage via one
`Repo` class. It proved the workflow and compiles conceptually, but per the audit it
is NOT the production architecture and will be re-platformed onto the backend (M2).
It still contains deferred issues — see `docs/backlog/M0-deferred.md` (notably
plaintext Aadhaar in Firestore, which must not touch real user data).

### Web prototype (`prototype/`) — ARCHIVED, do not extend.

## 5. How to run the backend (verified working)

Needs JDK 21 (NOT the host's JDK 25 — Spring Boot 3.3 isn't certified on 25).
Docker is the easy path:

```bash
cd ~/itcabs/backend
docker compose up --build -d        # Postgres + API, Flyway migrates on boot
# API on http://localhost:8081  (host 8080 is taken by an unrelated node process)
# Swagger: http://localhost:8081/docs
docker compose down                 # stop
```

Environment facts on this machine:
- Host JDK is 25; build/run the backend on JDK 21 via the gradle:8.10.2-jdk21 and
  eclipse-temurin:21-jre images (already wired in the Dockerfile).
- There is a local Postgres 15 running on :5432 (homebrew) — used it for the manual
  claim proof. The compose stack uses its own Postgres 16 container.
- Host port 8080 is occupied by an unrelated `node` process, so compose maps the API
  to **8081**. There are unrelated `nulfinity-*` and `idp` containers on this machine
  — a DIFFERENT project. Leave them alone.
- Docker Desktop shows a harmless "Failed to create template profile" notification
  (it's Docker Desktop's own signed-out housekeeping, not our code). Ignore it.

## 6. NEXT PLAN

Recommended order:

### M2 — Re-platform Android onto the API (the big next milestone)
Goal: the Android app talks ONLY to `/api/v1` (no direct Firestore data access),
with feature parity to today's app, but production-structured.
- Multi-module: `:app`, `:core:{network,database,designsystem,common,datastore}`,
  `:domain` (pure), `:data`, `:feature:*`. (ADR-0007)
- Hilt DI; Retrofit + OkHttp + kotlinx.serialization; Room as single source of truth.
- UDF ViewModels with immutable UiState.
- Claims are ONLINE-ONLY (server-authoritative); browsing is offline-capable. (ADR-0009)
- Suggested first slice: `:core:network` + `:data` repositories calling the backend
  auth + dispatch endpoints, replacing the Firestore `Repo`.

**M2 slice 1 — DONE + JVM-verified (auth data vertical).** Modules `:domain`,
`:core:network`, `:data` exist as **pure-Kotlin JVM** modules (no Android framework —
Retrofit/OkHttp/serialization are JVM-only, so they compile and unit-test without an
emulator). Contents:
- `:domain` — `User`/`Session`/`AuthTokens` models, `AppResult<T>` (Ok/Err with HTTP
  code — Err.code lets callers see the 409 on a claim), `AuthRepository` interface.
- `:core:network` — `AuthApi` (Retrofit binding for /auth/*), DTOs, `AuthInterceptor` +
  `TokenProvider`, `NetworkFactory`. Uses JakeWharton kotlinx-serialization converter.
- `:data` — `AuthRepositoryImpl`, DTO→domain mappers, `TokenStore` (`InMemoryTokenStore`
  for now — ponytail note: swap for encrypted DataStore before device use).
- Tests (4, all green via `./gradlew :core:network:test :data:test`): repository maps
  success + stores tokens, propagates the failure HTTP code without storing, refresh
  without a token is 401, and Retrofit annotations validate eagerly (no server needed).

**M2 slice 2 — DONE + JVM-verified (dispatch data vertical).** Same pure-JVM pattern:
- `:domain` — `Leg`/`LegStatus`/`NewJob`/`NewLeg` models, `DispatchRepository` (post,
  myLegs, setStatus, rate, feed, claim, myClaims). `claim` returns `Err(409)` on a lost race.
- `:core:network` — `DispatchApi` (Retrofit) + DTOs; `NetworkFactory.dispatchApi(...)`.
  `Response<Unit>` for setStatus/rate (Retrofit's built-in Unit converter handles empty bodies).
- `:data` — `DispatchRepositoryImpl` + `LegDto`→`Leg` mappers. The `asResult` Response→AppResult
  helper was extracted to `ResponseExt.kt` (shared by both repos).
- Tests (now 7 total, all green): claim-won maps the leg, **claim-lost is 409** (the
  first-claim-wins invariant at the client), feed maps the list, plus the auth-slice tests
  and the eager Retrofit contract test now covering both APIs.

**M2 slice 3 — DONE + compile-verified (first Android module).** `:feature:auth` is an
Android **library** module: `AuthViewModel` (`@HiltViewModel`, immutable `AuthUiState`,
UDF) driving `AuthRepository`, and `AuthScreen` (Compose, two-step phone→OTP form).
`./gradlew :feature:auth:assembleDebug` is green — `kspDebugKotlin` (Hilt) +
`compileDebugKotlin` (Compose compiler) + AAR all pass. **This is the toolchain proof:**
AGP 8.5.2 + Compose + Hilt + KSP 2.0.20-1.0.25 + Hilt 2.52 all compile against the
installed SDK. NOT run (no emulator), and the Hilt object graph isn't validated end-to-end
yet — that happens at `:app` (`@HiltAndroidApp`), which doesn't exist yet. It's additive:
the old Firebase `:app` was left untouched.

**M2 slice 4 — DONE + compile-verified (design system + styled auth).** `:core:designsystem`
holds `ItCabsTheme` (M3 ColorScheme + Typography + Shapes) built from the **Stitch design
export** the user provided at `~/Downloads/stitch_it_cars_enterprise_mobility_dashboard/`
(brand "IT Cars", primary `#2563EB`, lavender `#faf8ff` canvas / white cards, Inter type,
8px buttons / 16px cards). It exposes Compose (ui/material3/icons) as `api`, so feature
modules depend only on `:core:designsystem` for UI. `:feature:auth`'s `AuthScreen` was
restyled to the `login_screen` + `otp_verification` mockups (brand badge, white login card
with +91 prefix, value-prop cards, 6-box OTP input, role chips). `./gradlew
:core:designsystem:assembleDebug :feature:auth:assembleDebug` is green, no warnings.
- **Design source of truth:** `~/Downloads/stitch_it_cars_enterprise_mobility_dashboard/`
  — each screen folder has `code.html` (Tailwind) + `screen.png`; `it_cars_enterprise/DESIGN.md`
  is the token spec. 16 screens exist (dashboards, job details, ratings, notifications, etc.);
  only login/otp are built so far — the rest map to features not yet implemented.
- **Inter font is NOT bundled** (using `FontFamily.Default`); drop the `.ttf` into
  `:core:designsystem` res/font and swap in `Type.kt` to finish the look. `ponytail:` noted there.
- Still **compile-only** — no emulator, so the styled screens have not been visually rendered.

**M2 slice 5 — DONE + APK built (the `:app` rebuild — full graph validated).** `:app` was
re-platformed off Firebase: `App` (`@HiltAndroidApp`), `MainActivity` (`@AndroidEntryPoint`,
`setContent { ItCabsTheme { NavHost } }`, auth→home placeholder nav), and `di/AppModule`
(the composition root: provides `TokenStore`=`InMemoryTokenStore`, `AuthApi`/`DispatchApi`
via `NetworkFactory` at dev base URL `http://10.0.2.2:8081/`, binds both repositories).
`./gradlew :app:assembleDebug` is green — **`hiltAggregateDepsDebug`/`hiltJavaCompileDebug`
validated the entire DI graph** and it produced a real **16 MB APK** (`com.itcabs` v0.2,
label "IT Cars"). This retires the biggest open risk: the whole module graph compiles and
links end-to-end.
- The old Firebase sources are **preserved (moved, not deleted)** under
  `app/_legacy_firebase/` — outside the source set, so not compiled. `google-services.json`
  is gone from the build; the google-services plugin was dropped from `:app`.
- `android:usesCleartextTraffic="true"` is set for the dev http backend — remove/scope it
  for prod https.
- **Still not run** — no emulator. The APK builds and installs, but nothing has driven the
  UI or hit the backend from the device. Running needs an emulator or a physical device +
  the backend reachable at `10.0.2.2:8081` (emulator→host loopback).

**M2 slice 6 — DONE + APK rebuilt (dispatch feature).** `:feature:dispatch` (Android lib):
- `DriverFeedViewModel`/`DriverFeedScreen` — browse open legs + **Claim Trip**; on a 409 the
  VM shows "Trip already taken" and refreshes the feed (first-claim-wins surfaced in UI).
  Styled to `driver_home_screen` (fare, pickup→drop timeline, status chip, per-card claim).
- `CreateJobViewModel`/`CreateJobScreen` — coordinator posts a job with **dynamic pickup legs**
  (add/remove, pickup/drop/seats/fare-in-rupees→paise), styled to `create_job_screen`.
- Wired into `:app` nav: `home` is a manual role hub → `feed` / `create`. Full Hilt graph
  re-validated (both `@HiltViewModel`s inject `DispatchRepository`); `:app:assembleDebug`
  green, APK rebuilt. **7 modules total.** Still compile-only (no emulator).

**Next M2 slices (not started):** encrypted-DataStore `TokenStore` (replace `InMemoryTokenStore`),
`POST /auth/refresh` auto-retry on 401 (an OkHttp `Authenticator`), role-based home routing off
`/auth/me` (replace the manual hub), driver "my claimed trips" + coordinator dashboard/status
screens (designs: `claimed_trip_screen`, `coordinator_dashboard`, `job_details`, `rating_screen`),
and Room in `:data` as the offline cache. None need new domain logic — that's all done.

Git is now live at github.com/thecrazycoders7/itcabs (initial commit pushed). Work on branches
from here; this dispatch slice is uncommitted on the working tree.

**Build-env facts (set up this session, needed to build M2):**
- Android SDK installed at `/opt/homebrew/share/android-commandlinetools`
  (`platforms;android-34`, `build-tools;34.0.0`, `platform-tools`). Repo `local.properties`
  points `sdk.dir` there (gitignored — recreate on a fresh clone).
- **No emulator** — Android modules can be *compiled* but not *run* here.
- Gradle wrapper pinned to **8.9** (`./gradlew`); the brew-installed Gradle is 9.6.1,
  which is **incompatible with AGP 8.5.2** — always use `./gradlew`, never bare `gradle`.
- Build with **JDK 21**: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home`.
- The old `:app` (Firebase) **cannot build here** — no `google-services.json`. That's
  expected; M2 drops Firebase. Don't chase that failure.

### M1 tail (small; can ride alongside M2)
- ~~`POST /api/v1/auth/refresh` endpoint~~ **DONE** — exchanges an unrevoked, unexpired
  refresh token (30-day TTL, enforced via `device_sessions.created_at`) for a fresh
  access token. Verified E2E: 200 / 401 invalid / 400 blank. No token rotation yet
  (ponytail note in `IdentityService.refresh`).
- ~~OTP via a real SMS gateway behind a seam~~ **SEAM DONE** — delivery goes through
  `OtpSender` (`DevLogOtpSender` today). Wiring MSG91 is a drop-in second `@Component`;
  no other code changes. Actual SMS provider still unwired (no account yet).
- ~~Admin RBAC on `/admin/**`~~ **DONE** — `requireAdmin` guards it via an `is_admin`
  flag on `users` (migration V2). Admins minted out-of-band only
  (`UPDATE users SET is_admin=true WHERE phone='+91…'`). Verified E2E: 403 / 200 / 401.

**M1 tail is now closed** (refresh + OtpSender seam + RBAC). Only a live SMS provider
remains, which is an ops/account task, not code. Next milestone is M2.

### M0 (deferred by user) — fixes on the OLD Firebase app
Only matters if that app touches real data. See `docs/backlog/M0-deferred.md`:
plaintext Aadhaar (P0/compliance), dead code, computed-getter serialization,
version catalog, CI. Much of this dies naturally during M2.

### Later milestones (M3+): production hardening, then features — realtime
(WebSocket+Redis, ADR-0008), FCM push, payments, masked calling, templates,
trust/safety. See `docs/DEVELOPMENT_ROADMAP.md`.

## 7. First things to do in your session
1. `cat ~/itcabs/docs/README.md` and skim the audit + production architecture.
2. `cd ~/itcabs/backend && docker compose up --build -d` and hit `http://localhost:8081/docs` to see the live API.
3. Confirm with the user whether to start M2 or close the M1 tail first.
4. Do NOT add features outside the current milestone. Refactor-before-features is in force.

# ITCABS — Technical Audit Report

**Date:** 2026-07-21
**Scope:** The existing Android project (`/app`) and its Firebase data/rules, evaluated against the goal of a production-grade platform serving thousands of coordinators and hundreds of thousands of drivers across India, with future iOS / Admin Portal / Web clients.
**Verdict:** The current code is a **sound MVP scaffold** that correctly proved the dispatch workflow, but it is **not a production architecture** and cannot meet the stated multi-client, high-scale goals without the structural changes in this report. This is expected — it was deliberately built as a lazy core-loop demo.

Maturity is scored per dimension: **0 = absent, 1 = prototype, 2 = partial, 3 = production-ready.**

---

## 0. Headline findings

| # | Finding | Severity |
|---|---------|----------|
| F1 | **Business logic lives in the client + Firestore security rules, not a server-owned domain.** This directly blocks "support multiple clients without redesign" — every new client (iOS, Web, Admin) would have to re-embed queries, rules assumptions, and workflow logic. | **Critical** |
| F2 | **No backend service exists.** Payments, KYC/Aadhaar verification, masked calling, fraud/reappearance detection, and rating integrity all require a trusted server that is not present. | **Critical** |
| F3 | **Single flat module, no layering.** Domain, data, DTO, and UI concerns are merged into ~10 files. No dependency inversion, no testable seams. | High |
| F4 | **Aadhaar and RC numbers stored in plaintext** in Firestore (`UserProfile.aadhaar`, `rcNumber`). Legal exposure under the Aadhaar Act + DPDP Act 2023. | **Critical (compliance)** |
| F5 | **No offline strategy, no explicit source of truth.** Relies implicitly on Firestore's cache; no Room, no sync, no conflict handling. | High |
| F6 | **Cost & query model do not scale.** `collectionGroup` listeners fanned out to 200k drivers on an open-leg feed is expensive and query-limited (no radius/geo, no compound ranking). | High |
| F7 | **No CI/CD, no static analysis, one test.** Nothing gates quality; `ClaimTest` is the only automated check. | High |
| F8 | **Auth session is Firebase-owned**, not portable. Ties every future client to Firebase Auth. | Medium |

---

## 1. Overall architecture — **Score 1/3**

**Current:** Client-centric. The Android app talks directly to Firestore/Auth/Storage via a single `Repo` class. Firestore is simultaneously the database, the API, and (via `firestore.rules`) the business-rule engine. There is no server tier.

**Assessment:** This is a valid "BaaS MVP" pattern and was the correct lazy choice to prove the workflow. It fails the production brief on one axis that matters most here: **the domain is owned by clients, not the server.** Multi-client-without-redesign requires a server-owned domain behind a stable API. See [ADR-0002](../adr/0002-dedicated-backend-service.md).

**Target:** Thin clients → **ITCABS Backend API** (server-owned domain) → Postgres/Redis, with FCM for push only. See `PRODUCTION_ARCHITECTURE.md`.

## 2. Package structure — **Score 1/3**

**Current:** One Gradle module `:app`, one package `com.itcabs` with a `ui` sub-package. Files: `Models.kt`, `Repo.kt`, `MainActivity.kt`, `ItCabsApp.kt`, `ui/{AppViewModel, AuthScreens, HomeScreens, PostJobScreen}.kt`.

**Problems:** No boundary between layers; `Repo` is a 140-line god-object mixing auth, profile, posting, claiming, storage, and rating. `AppViewModel` is a single catch-all. Adding features grows these files linearly with no seams.

**Target:** Multi-module by layer and feature (`:app`, `:core:*`, `:data`, `:domain`, `:feature:*`). See [ADR-0007](../adr/0007-android-modular-architecture.md).

## 3. Feature modularization — **Score 0/3**

**Current:** None. Features (auth, posting, browsing, dashboard, rating) are entangled in shared files. No feature can be built, tested, or owned independently.

**Target:** `:feature:auth`, `:feature:jobs` (post/dashboard), `:feature:discovery` (browse/claim), `:feature:profile`, `:feature:trips`, later `:feature:payments`, `:feature:notifications`. Each depends on `:domain` + `:core`, never on another feature.

## 4. Domain model — **Score 1/3**

**Current:** `Models.kt` defines `UserProfile`, `Job`, `Leg`, and enums. These are **triple-purpose**: Firestore documents (`@DocumentId`, default-arg deserialization), domain entities, and UI models, all at once.

**Problems:**
- Coupling to Firestore annotations means the domain can't move to a backend without rewrites.
- Computed getters (`ratingAvg`, `kycComplete`) are **serialized to Firestore on write** — they will be persisted as stale fields. (Concrete bug; fix with `@get:Exclude` or, better, separate domain from DTO.)
- `status`, `role`, `vehicleType` are stringly-typed on the wire; enum safety is lost at the boundary.
- No value objects (Money for fare, PhoneNumber, VehicleReg) — money is a raw `Long`, fine for now but undocumented as paise-vs-rupees.

**Target:** Pure Kotlin domain entities (no framework annotations) in `:domain`; separate DTOs in `:data`; mappers between them. `Money` as paise (`Long`) with an explicit type.

## 5. Database schema — **Score 1/3**

**Current:** Firestore. `users/{uid}`, `jobs/{jobId}`, `jobs/{jobId}/legs/{legId}` (subcollection). Legs-as-documents was a good choice — it makes a claim a single-document transaction and enables `collectionGroup` feed queries.

**Problems for scale:**
- No relational integrity (a leg's `coordinatorId` can drift from its parent job; ratings aggregate by read-modify-write with no audit trail).
- No geo/radius querying — `area` is a free-text exact-match tag; real dispatch needs "legs within N km of me."
- Rating stored as `ratingSum`/`ratingCount` on the user with no per-trip rating records → no dispute trail, no recomputation.
- No payments, disputes, reports/blocks, or templates entities.
- Aadhaar/RC as plaintext string fields (see F4).

**Target:** PostgreSQL as system of record with proper entities, foreign keys, and PostGIS for geo. Firestore retired for data. See [ADR-0004](../adr/0004-postgres-system-of-record.md) and `PRODUCTION_ARCHITECTURE.md#data-model`.

## 6. API design — **Score 0/3**

**Current:** No API. Clients call the Firestore SDK. The "contract" is the document shape + security rules, which is implicit, unversioned, and untestable as an API.

**Target:** Versioned REST (`/api/v1`) with OpenAPI spec as the contract, plus a realtime channel (WebSocket/SSE) for live status. Idempotency keys on claim/post. See [ADR-0008](../adr/0008-realtime-transport.md).

## 7. Authentication flow — **Score 1/3**

**Current:** Firebase Phone Auth (OTP) via `PhoneAuthProvider` in `MainActivity`. Session = `FirebaseAuth.currentUser`. Role/verification stored on the user doc; verification is meant to be flipped "by an admin console" that does not exist.

**Problems:** Auth is Firebase-owned (not portable across clients/backends), no backend-issued session, no refresh/rotation strategy owned by us, no device/session management, no rate-limiting on OTP (abuse/cost risk at scale), no admin approval workflow.

**Target:** Backend-owned auth: phone OTP via an SMS gateway (e.g. MSG91), backend issues JWT **access + refresh** tokens, sessions tracked server-side, OTP rate-limited. See [ADR-0005](../adr/0005-backend-owned-auth.md).

## 8. State management — **Score 1/3**

**Current:** Compose + a single `AppViewModel` exposing Firestore `Flow`s via `callbackFlow`/`stateIn`. Screens collect flows directly; there is no modeled UI state, no loading/error states, no unidirectional data flow (UDF).

**Problems:** No `UiState` (loading/content/error/empty are ad-hoc), no event modeling, business decisions (`claim`, `advance`) live in the ViewModel calling the repo directly with no use-case layer. `flatMapLatest` re-subscribes are un-tested.

**Target:** Per-feature ViewModels exposing immutable `UiState` (sealed/`data class`), UDF with events, use cases in `:domain`. See [ADR-0007](../adr/0007-android-modular-architecture.md).

## 9. Offline support — **Score 0/3**

**Current:** None designed. Firestore's disk cache provides incidental offline reads, but there is no explicit source of truth, no write queue, no sync, no conflict policy.

**Critical design constraint:** **First-claim-wins cannot be made offline.** A claim is only meaningful against live server state; an offline-queued claim can't guarantee the lock. This must be an explicit product rule.

**Target:** Room as single source of truth for **browsing/feed/history** (offline-capable); **claim/post are online-only** with optimistic UI + server confirmation and an outbox for non-competitive writes (profile edits). See [ADR-0009](../adr/0009-offline-and-claim-connectivity.md).

## 10. Real-time synchronization — **Score 2/3**

**Current:** Firestore snapshot listeners give genuine realtime sync (verified in the archived web prototype). This is the strongest current dimension.

**Problems for scale:** Per-driver `collectionGroup` open-leg listeners mean cost scales with (drivers × leg churn); no server-side ranking/geo; no backpressure control; tied to Firestore.

**Target:** Backend-pushed realtime over WebSocket backed by Redis pub/sub, scoped by subscription (area/time/vehicle), with the backend controlling fan-out. See [ADR-0008](../adr/0008-realtime-transport.md).

## 11. Security — **Score 1/3**

**Current:** `firestore.rules` is a competent first pass — it enforces the OPEN→CLAIMED transition server-side, the verified/unblocked gate, and prevents self-verify/self-unblock. Good instincts.

**Problems:**
- **Plaintext Aadhaar/RC (F4)** — compliance-critical.
- Complex domain logic in security rules is hard to test, version, and evolve; it will not hold the weight of payments/fraud/disputes.
- No input validation/rate-limiting tier; no secrets management (google-services.json handling, no backend secrets story yet).
- No audit log for sensitive actions (verify, block, payment, claim).
- Photo/KYC documents in Storage without documented access scoping.

**Target:** Server-side authorization + validation, encryption-at-rest for PII with tokenization of Aadhaar (store a reference + masked value + KYC document image, never the raw number), audit logging, WAF/rate-limiting, secrets manager. See [ADR-0006](../adr/0006-pii-and-compliance.md).

## 12. Performance — **Score 1/3**

**Current:** Small demo; no measured performance. Risks at scale: unbounded feed listeners, N re-renders on full-list snapshots (`LazyColumn` keyed by leg id — good), no pagination, full-document snapshots for large feeds.

**Target:** Server-side pagination + ranking, delta updates over the realtime channel, Room-backed lists, baseline profiles, and load targets (feed p95 < 300 ms, claim p95 < 400 ms) defined in `PRODUCTION_ARCHITECTURE.md`.

## 13. Scalability — **Score 1/3**

**Current:** Inherits Firestore's horizontal scale but also its cost model and query limits. The claim hotspot (many drivers racing for the same popular leg) is a single-document contention point; Firestore transactions retry under contention, degrading latency exactly when demand peaks.

**Target:** Postgres atomic `UPDATE ... WHERE status='OPEN'` (single round-trip, no read-then-write retry storm), stateless horizontally-scaled backend, Redis for hot paths, read replicas for feed. See [ADR-0003](../adr/0003-modular-monolith-backend.md).

## 14. Testing strategy — **Score 1/3**

**Current:** One JVM test (`ClaimTest`) covering the claim guard + a CAS race simulation. No repository tests, no ViewModel tests, no Compose UI tests, no instrumentation, no backend tests (no backend).

**Target:** Test pyramid — domain use-case unit tests, data-layer tests (Room + fake API), ViewModel tests (Turbine), Compose UI tests, backend unit + integration (Testcontainers Postgres) + contract tests, plus the existing concurrency test elevated to a real integration test against Postgres. See `PRODUCTION_ARCHITECTURE.md#testing`.

## 15. CI/CD readiness — **Score 0/3**

**Current:** None. No CI config, no ktlint/detekt, no Gradle wrapper jar committed (Studio regenerates), no build/test gates, no signing/distribution pipeline. `google-services.json` correctly git-ignored but no documented provisioning.

**Target:** GitHub Actions — lint/detekt/ktlint → unit tests → assemble → (later) instrumentation on emulator → Play internal track / Firebase App Distribution. Backend: build → test (Testcontainers) → containerize → deploy. See [ADR-0010](../adr/0010-ci-cd-and-environments.md).

## 16. Technical debt — **Score (register below)**

| ID | Debt | Location | Priority |
|----|------|----------|----------|
| D1 | Computed getters serialized to Firestore | `Models.kt` `ratingAvg`, `kycComplete` | fix or obsolete via domain/DTO split |
| D2 | Dead code | `Repo.newRequestId()` + `UUID` import | delete |
| D3 | God-object repository | `Repo.kt` | split by aggregate |
| D4 | Stringly-typed enums on the wire | `Models.kt` | typed at boundary |
| D5 | Versions inline, no version catalog | `app/build.gradle.kts` | `libs.versions.toml` |
| D6 | No `Money` type; fare unit undocumented | `Leg.fare` | introduce paise-based type |
| D7 | Rating via read-modify-write, no records | `Repo.rateDriver` | per-trip rating table |
| D8 | Business logic in security rules | `firestore.rules` | move to backend |
| D9 | Plaintext PII | `UserProfile.aadhaar/rcNumber` | tokenize (compliance) |

---

## Overall maturity

**Average ~0.9 / 3 — "validated prototype."** The workflow is proven and several instincts are right (legs-as-documents, server-enforced claim transition, no self-verify). The gap to production is structural and is addressed by the production architecture, the refactoring roadmap, and the milestone roadmap accompanying this report.

**Recommendation:** Do not layer features onto the current client-only design. Execute Phase 0–1 of the refactoring roadmap (introduce the backend + Android layering) first, exactly as the project brief directs ("refactor before adding features").

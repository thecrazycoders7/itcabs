# ITCABS — Production Architecture

**Status:** Proposed (target state)
**Audience:** Engineering, future iOS/Web/Admin teams
**Companion docs:** `TECHNICAL_AUDIT.md`, `../REFACTORING_ROADMAP.md`, `../DEVELOPMENT_ROADMAP.md`, `../adr/`

This document defines the target architecture for ITCABS as a multi-client, India-scale transport dispatch platform. It is the reference every ADR elaborates on.

---

## 1. Principles

1. **Server owns the domain.** Business rules (claim locking, fraud, fare, ratings, payments) live in the backend, never in clients or database rules. This is what makes iOS/Web/Admin possible without redesign.
2. **Clients are thin.** They render state and capture intent; they do not enforce invariants.
3. **Stable, versioned API contract** (`/api/v1` + OpenAPI). Clients depend on the contract, not the storage.
4. **Boring, maintainable technology.** Mature tools with strong ecosystems over novel ones.
5. **One language where it helps.** Kotlin on both Android and backend → shared mental model, shared DTO definitions possible.
6. **Offline where safe, online where required.** Browsing offline-capable; competitive writes (claims) online-only and server-authoritative.
7. **Compliance is a first-class constraint.** PII (Aadhaar/KYC) handled to Aadhaar Act + DPDP Act 2023 standards from day one.

---

## 2. System context (C4 level 1)

```
        ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
        │  Android app │   │   iOS app    │   │  Admin Portal│   │   Web app    │
        │  (built now) │   │   (later)    │   │   (later)    │   │   (later)    │
        └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
               │ REST + WebSocket (HTTPS/WSS, JWT)   │                  │
               └───────────────┬────────────────────┴──────────────────┘
                               ▼
                   ┌───────────────────────────┐
                   │   ITCABS Backend API       │  Kotlin + Spring Boot (modular monolith)
                   │  (server-owned domain)     │
                   └───┬───────┬───────┬────────┘
          ┌────────────┘       │       └─────────────┐
          ▼                    ▼                      ▼
   ┌────────────┐      ┌──────────────┐       ┌──────────────┐
   │ PostgreSQL │      │    Redis     │       │  Object store│  (KYC docs, photos — S3-compatible)
   │ + PostGIS  │      │ cache/pubsub │       └──────────────┘
   └────────────┘      └──────────────┘
          │                                   External: SMS gateway (OTP), Payment gateway
          ▼                                   (Razorpay/UPI), FCM (push), KYC/Aadhaar verification API
   read replicas
```

## 3. Backend

**Stack:** Kotlin + Spring Boot 3 (Web, Security, Validation, Data JPA/jOOQ), Gradle. **Modular monolith** — one deployable, enforced module boundaries, extractable to services later ([ADR-0003](../adr/0003-modular-monolith-backend.md)).

**Module boundaries (bounded contexts):**
- `identity` — phone OTP, JWT issuance/refresh, sessions, roles, driver verification (KYC) workflow.
- `dispatch` — jobs, legs, claiming (the core), lifecycle (open→claimed→confirmed→completed→cancelled).
- `discovery` — feed queries, geo/area/time/vehicle filtering, ranking, driver subscriptions.
- `reputation` — per-trip ratings/reviews, aggregates, history.
- `payments` — fare settlement, receipts, disputes (later milestone).
- `trust-safety` — reports, blocks, fraud/reappearance detection.
- `notifications` — FCM push, subscription matching.
- `shared` — value objects (Money, PhoneNumber, GeoPoint), errors, events.

Each context exposes an application service; cross-context calls go through interfaces, and contexts communicate by **domain events** (in-process now, message bus if/when extracted).

**Layering per context:** `api` (controllers/DTOs) → `application` (use cases, transactions) → `domain` (entities, invariants) → `infrastructure` (repositories, gateways). Dependencies point inward.

## 4. Data model (PostgreSQL) — core tables

```
users(id, phone UNIQUE, role, name, status, created_at, ...)
driver_profiles(user_id PK/FK, vehicle_type, vehicle_reg,
                aadhaar_ref, aadhaar_masked, rc_number_masked,
                kyc_status[PENDING|VERIFIED|REJECTED], photo_url, verified_at, verified_by)
jobs(id, coordinator_id FK, office, drop_hub, shift, geo POINT, created_at, status)
legs(id, job_id FK, pickup, pickup_geo POINT, drop, drop_geo POINT,
     area, time_window, vehicle_type, fare_paise BIGINT, seats,
     status[OPEN|CLAIMED|CONFIRMED|COMPLETED|CANCELLED],
     claimed_by FK NULL, claimed_at, version INT)         -- optimistic lock
claims_audit(id, leg_id FK, driver_id FK, action, at, prev_status, new_status)
ratings(id, leg_id FK, rater_id FK, ratee_id FK, stars, review, created_at)  -- per-trip, immutable
templates(id, coordinator_id FK, name, payload JSONB)      -- recurring postings
reports(id, reporter_id, subject_id, reason, evidence, status, created_at)
blocks(id, subject_id, reason, created_by, created_at)
device_sessions(id, user_id FK, refresh_token_hash, device, created_at, revoked_at)
```

**Aadhaar:** never store the raw number. Store `aadhaar_ref` (tokenized by the KYC/verification provider or an internal vault) + `aadhaar_masked` (`XXXX-XXXX-1234`) + the document image in the object store with scoped access. See [ADR-0006](../adr/0006-pii-and-compliance.md).

**Geo:** `pickup_geo`/`drop_geo` as PostGIS `geography(POINT)`; feed queries use `ST_DWithin` for radius search — the real replacement for exact-match `area`.

## 5. The core: server-authoritative claim (first-claim-wins)

The single most important invariant. Implemented as **one atomic statement**, not read-then-write:

```sql
UPDATE legs
   SET status='CLAIMED', claimed_by=:driverId, claimed_at=now(), version=version+1
 WHERE id=:legId AND status='OPEN'
RETURNING *;
```

- **0 rows returned → lost the race** → `409 Leg already taken`.
- **1 row → won.** No transaction retry storm under contention (unlike the Firestore read-then-write transaction, which retries exactly when a popular leg is hot).
- Guarded upstream by authorization (verified, unblocked driver) and idempotency key (safe client retry).
- Emits `LegClaimed` domain event → realtime push + audit row.

This is the production form of `Repo.canClaim()` / `server.js`'s guard, and of the concurrency behaviour proven by `ClaimTest`. The test graduates to an integration test hitting real Postgres with N concurrent claimers asserting exactly one winner.

## 6. API design

- **REST** `/api/v1`, JSON, OpenAPI spec is the contract (generated, versioned, published to client teams).
- **Auth:** `Authorization: Bearer <JWT access>`; refresh via `/auth/refresh`.
- **Idempotency:** `Idempotency-Key` header on `POST /legs/{id}/claim` and job creation.
- **Pagination:** cursor-based for feeds.
- Representative endpoints:
  - `POST /auth/otp/request`, `POST /auth/otp/verify`, `POST /auth/refresh`
  - `POST /driver/kyc`, `GET /driver/me`
  - `POST /jobs` (with legs), `GET /jobs/mine`, `PATCH /legs/{id}/status`
  - `GET /legs?lat=&lng=&radius=&vehicle=&window=` (feed), `POST /legs/{id}/claim`
  - `POST /legs/{id}/rating`
  - Admin: `POST /admin/drivers/{id}/verify`, `POST /admin/users/{id}/block`

## 7. Realtime & notifications

- **Live status** over **WebSocket** (WSS), fan-out via **Redis pub/sub**; subscriptions scoped by area/vehicle/time so a driver receives only relevant leg deltas ([ADR-0008](../adr/0008-realtime-transport.md)).
- **Push** via **FCM** only (device wake / background alerts for subscribed areas) — not a data channel ([ADR-0008](../adr/0008-realtime-transport.md)).
- Clients reconcile: on (re)connect, fetch snapshot via REST, then apply WS deltas.

## 8. Authentication

Backend-owned ([ADR-0005](../adr/0005-backend-owned-auth.md)): phone OTP via SMS gateway (MSG91 or similar), backend issues short-lived JWT **access** + long-lived rotating **refresh** tokens; sessions tracked in `device_sessions` (revocable). OTP request rate-limited per phone/IP. Driver verification is an admin-approval workflow (`kyc_status`), not a client-settable flag.

## 9. Android target architecture

Google "Now in Android"–style, multi-module, offline-first ([ADR-0007](../adr/0007-android-modular-architecture.md)):

```
:app                      DI wiring, navigation host, MainActivity
:core:designsystem        Compose theme, components
:core:common              Result, dispatchers, utils
:core:network             Retrofit + OkHttp + kotlinx.serialization, auth interceptor, WS client
:core:database            Room (SSoT), DAOs, entities
:core:datastore           tokens/prefs (encrypted)
:domain                   pure entities + use cases (no Android/framework deps)
:data                     repositories (impl of :domain interfaces), DTOs, mappers, sync
:feature:auth :feature:profile :feature:jobs :feature:discovery :feature:trips ...
```

- **DI:** Hilt.
- **State:** UDF — each feature ViewModel exposes immutable `UiState`; events flow in, state flows out.
- **Networking:** Retrofit; typed errors mapped from API problem responses.
- **Offline:** Room is the single source of truth for feed/trips/profile; repositories mediate network↔db; **claims/posts are online-only** with optimistic UI + server confirmation ([ADR-0009](../adr/0009-offline-and-claim-connectivity.md)).
- **Realtime:** WS client writes deltas into Room; UI observes Room → automatic live updates.

## 10. Security & compliance

- TLS everywhere; JWT with rotation; server-side authorization on every endpoint.
- PII: encryption at rest, Aadhaar tokenized + masked, KYC docs in scoped object storage, audit log for verify/block/payment/claim.
- Rate limiting + WAF at the edge; secrets in a managed secret store (not in repo).
- DPDP Act 2023: consent capture, data-retention policy, deletion/erasure support.
- Trust & safety: `reports`/`blocks`; reappearance mitigated because identity is phone-verified and blocks are on the verified identity + device fingerprints.

## 11. Performance & scale targets

| Metric | Target |
|--------|--------|
| Feed query p95 | < 300 ms |
| Claim p95 (incl. contention) | < 400 ms |
| Realtime delta delivery p95 | < 1 s |
| Backend | stateless, horizontally scaled behind LB |
| Postgres | primary + read replicas; feed reads on replicas |
| Redis | cache hot feeds + pub/sub fan-out |

## 12. Testing

Test pyramid across both codebases:
- **Domain** (both): pure unit tests of use cases/invariants.
- **Backend:** integration with **Testcontainers Postgres** (incl. the concurrent-claim test), contract tests against OpenAPI.
- **Android:** Room tests, repository tests with fake API, ViewModel tests (Turbine), Compose UI tests, screenshot tests.
- **E2E:** critical flows (post → claim → complete → rate) on a staging environment.

## 13. Environments & CI/CD

`local → dev → staging → prod`. GitHub Actions gates every PR (lint/detekt/ktlint → unit → integration → build); protected `main`; backend containerized and deployed per environment; Android to Play internal → closed → production tracks ([ADR-0010](../adr/0010-ci-cd-and-environments.md)).

## 14. What we explicitly are NOT doing (yet)

- Not building microservices (modular monolith until scale demands extraction).
- Not building iOS/Web/Admin clients now — but the API contract is designed so they attach without backend redesign.
- Not offline-queuing claims (impossible to do correctly for first-claim-wins).
- Not retaining Firestore for data (FCM only, for push).

# ITCABS — Prioritized Refactoring Roadmap

Ordered by dependency and risk. **No new product features until Phase 0–1 are done** (per project directive: "refactor before adding features"). Each item lists the audit finding it resolves.

Priorities: **P0 = blocks production/compliance, P1 = structural, P2 = quality, P3 = polish.**

---

## Phase 0 — Stop the bleeding (days) — do first

| P | Task | Resolves | Notes |
|---|------|----------|-------|
| P0 | Tokenize/mask Aadhaar & RC; stop storing raw PII | F4, D9 | Even before the backend exists, remove plaintext PII from any live Firestore. Compliance risk. |
| P2 | Delete dead code (`Repo.newRequestId`, unused `UUID`) | D2 | Trivial. |
| P2 | Fix computed-getter serialization (`@get:Exclude` on `ratingAvg`, `kycComplete`) | D1 | Interim until domain/DTO split lands. |
| P2 | Introduce `libs.versions.toml` version catalog | D5 | Foundation for multi-module. |
| P1 | Stand up CI (ktlint/detekt + unit tests) on the current app | F7, CI/CD | Gate quality before the big refactor. |

## Phase 1 — Backbone: backend + contract (weeks) — COMPLETE

| P | Task | Resolves | Status |
|---|------|----------|--------|
| P0 | Scaffold the Kotlin/Spring Boot **modular monolith**; module boundaries per bounded context | F1, F2, ADR-0003 | [x] |
| P0 | PostgreSQL schema + migrations (Flyway); `legs` with `version` + PostGIS | F5(db), F6, ADR-0004 | [x] |
| P0 | **Server-authoritative claim** endpoint (`UPDATE ... WHERE status='OPEN' RETURNING`) + idempotency | F1, D8, ADR core | [x] |
| P0 | Backend-owned auth: OTP via SMS gateway, JWT access/refresh, sessions, rate-limiting | F8, dim.7, ADR-0005 | [x] |
| P1 | OpenAPI contract for v1 (auth, jobs/legs, feed, claim, rating) | F6(api) | [x] |
| P1 | Port claim concurrency test to Testcontainers integration test | dim.14 | [x] |
| P1 | Admin-approval KYC verification workflow (server) | dim.7, dim.11 | [x] |

## Phase 2 — Re-platform Android onto the API (weeks) — IN PROGRESS

| P | Task | Resolves | Status |
|---|------|----------|--------|
| P1 | Split `:app` into multi-module skeleton (`:core:*`, `:domain`, `:data`, `:feature:*`) | F3, D3, ADR-0007 | [x] |
| P1 | Introduce **Hilt** DI; retire manual `Repo` singleton | F3 | [x] |
| P1 | Pure `:domain` entities + use cases; separate DTOs + mappers | F4(domain), D4, D6 | [x] |
| P1 | `:data` repositories on Retrofit; replace Firestore SDK calls | F1 | [x] |
| P1 | Per-feature ViewModels with immutable `UiState` (UDF) | dim.8 | [x] |
| P1 | Room as SSoT for feed/trips/profile | F5, ADR-0009 | [x] |
| P1 | **Driver KYC submission screen** in Compose | (parity) | [x] |
| P1 | WebSocket client → Room deltas (realtime) | dim.10, ADR-0008 | [ ] |
| P2 | Enforce online-only claim UX (optimistic + server confirm) | dim.9, ADR-0009 | [ ] |

## Phase 3 — Production hardening (weeks)

| P | Task | Resolves |
|---|------|----------|
| P1 | Full CI/CD pipeline (both repos), environments, protected main, distribution tracks | F7, dim.15, ADR-0010 |
| P1 | Audit logging (verify/block/claim/payment), rate limiting, WAF, secrets manager | dim.11 |
| P2 | Test pyramid to target coverage; screenshot + E2E on staging | dim.14 |
| P2 | Performance: pagination, read replicas, Redis caching, baseline profiles; meet p95 targets | dim.12, dim.13 |
| P2 | Observability: structured logs, metrics, tracing, alerting | (new) |

## Exit criteria to leave "refactor" and resume features

- Backend owns the domain; Android talks only to `/api/v1` + WS (no direct Firestore data access).
- Claim is server-authoritative and integration-tested under contention.
- No plaintext PII anywhere; audit log live.
- CI green-gates both repos; staging deployable.

Only then start the feature milestones in `DEVELOPMENT_ROADMAP.md` (M4+).

# ITCABS — Milestone Development Roadmap

Milestones are outcome-based, not date-based (adjust durations to team size). **M0–M3 are the refactor** (`REFACTORING_ROADMAP.md`); no new product features ship until M3 exits. **M4+ are features** on the production architecture.

Each milestone has a **Definition of Done (DoD)** and the ADRs it realizes.

---

### M0 — Foundations & compliance triage
**Goal:** Safe to keep building; quality gate exists.
- Remove plaintext PII; delete dead code; version catalog; CI with lint + unit tests.
- **DoD:** CI green on `main`; no raw Aadhaar/RC stored; audit findings F4/D1/D2/D5 closed.

### M1 — Backend backbone
**Goal:** Server owns the domain; the claim is authoritative.
- Spring Boot modular monolith; Postgres + Flyway + PostGIS; auth (OTP + JWT); `POST /legs/{id}/claim`; OpenAPI v1; Testcontainers claim-contention test.
- **DoD:** A driver can auth, post/claim a leg via the API; concurrent-claim integration test passes; OpenAPI published. Realizes ADR-0002/03/04/05.

### M2 — Android re-platform (parity)
**Goal:** Android on the API with the same features it has today — but production-structured.
- Multi-module + Hilt; domain/data/feature split; Retrofit repositories; Room SSoT; UDF ViewModels; WebSocket realtime.
- **DoD:** Feature parity with the current app (auth, KYC submit, post job, browse+claim, dashboard, rate) running entirely against the backend; Firestore data access removed. Realizes ADR-0007/08/09.

### M3 — Production hardening
**Goal:** Operable at scale, safely.
- CI/CD + environments + distribution; audit logging; rate limiting/WAF/secrets; observability; performance to p95 targets; test pyramid.
- **DoD:** Staging deployable via pipeline; p95 targets met under load test; security review passed. **Refactor phase complete — features resume.**

---

## Feature milestones (post-refactor)

### M4 — Trust & discovery depth
- Geo/radius feed (PostGIS), driver **area/time/vehicle subscriptions** + **FCM push**, driver rating/history surfaced to coordinators, reports/blocks with reappearance mitigation.
- **DoD:** A coordinator can vet an unknown driver by rating/history; a driver gets pushed only relevant legs; a blocked identity cannot re-register on the same phone/device.

### M5 — Payments & receipts
- Fare settlement via payment gateway (UPI/Razorpay), in-app receipts, dispute flow, settlement audit.
- **DoD:** A completed leg produces a receipt; disputes are tracked; no ambiguous "pay tomorrow" state. Realizes payments context + ADR-0006 audit needs.

### M6 — Coordinator productivity
- Recurring/templated postings (daily shift routes), bulk multi-leg posting, dashboard analytics.
- **DoD:** A coordinator reposts a daily route in one action from a template.

### M7 — In-app comms
- Masked call / in-app chat channel (numbers never broadcast), tied to a claimed leg.
- **DoD:** Coordinator and claiming driver communicate without exposing phone numbers.

### M8 — Multi-client expansion (as demanded)
- iOS app and/or Admin Portal + Web, attaching to the **existing** `/api/v1` — validating the "no redesign" goal.
- **DoD:** A second client ships without backend contract changes beyond additive versioned endpoints.

---

## Sequencing rules
1. Never start a milestone whose ADRs aren't accepted.
2. Every cross-cutting decision inside a milestone gets a new ADR before code.
3. Compliance items (PII, audit, consent) are never deferred past the milestone that introduces the data.

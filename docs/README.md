# ITCABS — Engineering Documentation

Start here. ITCABS is a production-grade transport-dispatch platform (Android now; iOS/Web/Admin later) replacing a free-text Telegram coordination group.

## Read in this order

1. **[Technical Audit](architecture/TECHNICAL_AUDIT.md)** — where the current code stands across 16 dimensions, with severities and a debt register.
2. **[Production Architecture](architecture/PRODUCTION_ARCHITECTURE.md)** — the target system: backend-owned domain, Postgres, realtime, Android modules.
3. **[Refactoring Roadmap](REFACTORING_ROADMAP.md)** — prioritized path from current → target (P0–P3, phased).
4. **[Development Roadmap](DEVELOPMENT_ROADMAP.md)** — outcome-based milestones (M0–M8) with Definitions of Done.

## Architecture Decision Records

| ADR | Decision |
|-----|----------|
| [0001](adr/0001-record-architecture-decisions.md) | Record architecture decisions (use ADRs) |
| [0002](adr/0002-dedicated-backend-service.md) | Dedicated backend service; clients are thin |
| [0003](adr/0003-modular-monolith-backend.md) | Kotlin/Spring Boot modular monolith |
| [0004](adr/0004-postgres-system-of-record.md) | PostgreSQL as system of record |
| [0005](adr/0005-backend-owned-auth.md) | Backend-owned auth (phone OTP + JWT) |
| [0006](adr/0006-pii-and-compliance.md) | PII/Aadhaar handling & compliance |
| [0007](adr/0007-android-modular-architecture.md) | Android multi-module offline-first |
| [0008](adr/0008-realtime-transport.md) | Realtime via WebSocket + Redis; FCM push only |
| [0009](adr/0009-offline-and-claim-connectivity.md) | Offline browsing; claims online-only |
| [0010](adr/0010-ci-cd-and-environments.md) | CI/CD, environments, quality gates |

## Current status

**Refactor-before-features phase (M0–M3).** No new product features until the backend owns the domain and Android is re-platformed onto the API. The web client under `/prototype` is **archived** (see `prototype/ARCHIVED.md`) — reference only, resurrected only if an Admin Portal/Web client is explicitly requested.

Progress:
- **M0** — deferred by decision (see `backlog/M0-deferred.md`); PII fix on the current Android app still open.
- **M1 (backbone done, verified)** — backend under `/backend` (Kotlin/Spring Boot, Postgres). Compiles clean on JDK 21; **full flow verified end-to-end over HTTP** (auth → post → verified-driver claim → 409 double-claim → complete → rate). First-claim-wins proven against real Postgres (50 concurrent → 1 winner) + Testcontainers test. **M1 tail complete:** `POST /auth/refresh` (200 / 401 / 400, 30-day expiry), OTP delivery behind an `OtpSender` seam (real SMS gateway is now a drop-in bean), and admin RBAC via an `is_admin` flag (403 non-admin / 200 admin / 401 anon). All verified E2E. See `../backend/README.md`.
- **M2+** — not started.

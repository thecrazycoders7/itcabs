# itcabs-backend (M1)

Server-owned domain for ITCABS. Kotlin + Spring Boot 3.3 modular monolith,
PostgreSQL, JDBC. This is the backbone from milestone **M1** — auth + dispatch
(post/claim) with the **server-authoritative first-claim-wins** core.

See `../docs/architecture/PRODUCTION_ARCHITECTURE.md` for the target design and
`../docs/adr/` for the decisions this implements (0002–0006, 0010).

## Structure (package-by-bounded-context)

```
com.itcabs
  identity/    OTP + JWT auth, sessions, driver KYC + admin verify
  dispatch/    jobs, legs, the atomic claim, status workflow, rating
  reputation/  (ratings live in dispatch for M1; own context later)
  shared/      errors
  config/
```

## The core: first-claim-wins

`DispatchService.claim` is one atomic statement — the verified+active-driver gate
is *inside* the UPDATE, so the check and the lock are indivisible:

```sql
UPDATE legs SET status='CLAIMED', claimed_by=:d, claimed_at=now(), version=version+1
 WHERE id=:id AND status='OPEN'
   AND EXISTS (verified, active driver :d)
RETURNING id;   -- 0 rows => lost the race => 409
```

**Verified against real Postgres:** 50 concurrent claimers → exactly 1 winner,
49 × `409`. Reproduced as an automated Testcontainers test:
`src/test/kotlin/.../ClaimConcurrencyTest.kt`.

## Run

Requires JDK 21 (not 25 — Spring Boot 3.3 isn't certified on 25 yet). Easiest:

```bash
docker compose up --build        # Postgres + API on :8080, Flyway migrates on boot
```

Or locally against your own Postgres:

```bash
DB_URL=jdbc:postgresql://localhost:5432/itcabs ./gradlew bootRun
```

API docs (Swagger UI): `http://localhost:8080/docs`

## API (v1)

| Method | Path | Who | Purpose |
|--------|------|-----|---------|
| POST | `/api/v1/auth/otp/request` | public | send OTP (dev: logged) |
| POST | `/api/v1/auth/otp/verify` | public | verify OTP → tokens (first call sets role/name) |
| POST | `/api/v1/auth/refresh` | public | exchange refresh token → fresh access token |
| GET  | `/api/v1/auth/me` | auth | current user |
| POST | `/api/v1/driver/kyc` | driver | submit KYC (no raw Aadhaar — token+masked only) |
| POST | `/api/v1/admin/drivers/{id}/verify` | admin* | mark driver VERIFIED |
| POST | `/api/v1/jobs` | coordinator | post a job with legs |
| GET  | `/api/v1/legs/mine` | coordinator | dashboard |
| PATCH| `/api/v1/legs/{id}/status` | coordinator | CONFIRMED/COMPLETED/CANCELLED |
| POST | `/api/v1/legs/{id}/rating` | coordinator | rate completed leg |
| GET  | `/api/v1/legs?area=&vehicleType=` | driver | open-leg feed |
| POST | `/api/v1/legs/{id}/claim` | driver | claim (first-wins) |
| GET  | `/api/v1/legs/claimed` | driver | my trips |

\* Admin-only via the `is_admin` flag on `users` (403 otherwise). Admins are minted
out-of-band only — `UPDATE users SET is_admin=true WHERE phone='+91…'`; no public path
grants it. Verified E2E: 403 non-admin / 200 admin / 401 anon.

## Deferred to later milestones

Realtime (WebSocket+Redis, ADR-0008), FCM push, payments, masked calling,
templates, trust/safety, PostGIS geo feed. OTP delivery is behind an `OtpSender`
seam (dev-log impl today); wiring a real SMS gateway (MSG91) is a drop-in second bean.

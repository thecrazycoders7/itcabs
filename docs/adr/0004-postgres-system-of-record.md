# ADR-0004: PostgreSQL as the system of record

**Status:** Accepted · 2026-07-21

## Context
The domain is strongly relational (users, jobs, legs, claims, ratings, payments, reports) with hard consistency needs — most critically the first-claim-wins lock and financial settlement. We also need geo queries ("legs near me") that the current exact-match `area` tag cannot serve, and an audit trail for trust/compliance.

## Decision
Use **PostgreSQL** as the system of record, with **PostGIS** for geo and **Redis** for caching + realtime pub/sub + hot-path counters. Firestore is **retired for data**; FCM is retained for push only (see ADR-0008).

## Consequences
- Strong consistency + transactions where they matter (claim, payment).
- The claim becomes a single atomic `UPDATE ... WHERE status='OPEN' RETURNING` — no read-then-write retry storm under contention.
- Real geo/radius discovery via `ST_DWithin`.
- Relational integrity (FKs), immutable per-trip `ratings`, and audit tables.
- Requires migrations (Flyway) and DB ops (replicas, backups) we now own.

## Alternatives considered
- **Firestore.** Per-read cost at 200k-driver feed scale, no geo/ranking, read-then-write transactions retry under exactly the contention we care about. Rejected as system of record.
- **MongoDB.** Flexible but weaker relational/transactional guarantees for money/claims. Rejected.
- **Cassandra/DynamoDB.** Scale is not our bottleneck; relational modeling and consistency are. Rejected as primary store.

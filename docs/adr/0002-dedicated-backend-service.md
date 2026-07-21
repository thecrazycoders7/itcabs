# ADR-0002: Introduce a dedicated backend service; clients are thin

**Status:** Accepted · 2026-07-21
**Supersedes:** the prototype approach of client-side Firestore-as-backend.

## Context
The prototype (and the current Android app) talk directly to Firestore, with business rules split between the client and `firestore.rules`. The product goal is now explicit: **support Android, iOS, Admin Portal, and Web without redesign**, at India scale, with payments, KYC, fraud detection, and disputes.

A client-owned domain cannot meet this: each new client would re-implement queries, re-encode workflow logic, and depend on the database's document shape and rules. Security rules are also the wrong place for evolving domain logic (untestable, unversioned, no complex transactions).

## Decision
Introduce the **ITCABS Backend API** as the single owner of the domain. All clients are thin consumers of a versioned API (REST `/api/v1` + realtime channel). No client accesses the database directly.

## Consequences
- Enables multi-client without redesign — the whole point.
- Adds a service to build/operate (offset by removing logic from rules/clients).
- Payments/KYC/masked-calling/fraud become possible (they require a trusted server).
- Android must be re-platformed off the Firestore SDK (see ADR-0007, Refactoring Phase 2).

## Alternatives considered
- **Stay Firestore-direct + Cloud Functions for logic.** Viable but keeps clients coupled to Firestore document shape and rules; Functions are a poor home for a rich, transactional domain; cost model and query limits (no geo/ranking) remain. Rejected for the stated scale/multi-client goal.
- **BaaS (Supabase/Appwrite).** Better queries (Postgres) but still pushes domain logic toward the client/policies. Rejected in favor of an owned domain.

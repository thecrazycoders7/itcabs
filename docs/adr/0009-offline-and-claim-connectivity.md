# ADR-0009: Offline-first browsing; claims are online-only

**Status:** Accepted · 2026-07-21

## Context
Drivers work on patchy mobile networks, so offline capability matters. But the core invariant — **first-claim-wins** — is only meaningful against live server state. An offline-queued claim cannot guarantee the lock; two drivers offline could both "claim" the same leg and collide on reconnect, recreating exactly the Telegram double-claim chaos we're eliminating.

## Decision
- **Offline-capable (Room SSoT):** feed/browse, my-trips, profile, cached ratings/history. The app is fully readable offline.
- **Online-only, server-authoritative:** **claiming a leg** and **posting a job**. Claim UX is optimistic (instant local feedback) but only confirmed on server `200`; a `409` (lost the race) reverts the optimistic state with a clear message. No offline write queue for claims.
- **Outbox pattern** is allowed only for **non-competitive** writes (e.g. profile edits, ratings) that can safely sync later.

## Consequences
- Correctness preserved: no offline double-claims.
- Clear, honest UX: "You're offline — you can browse, but claiming needs a connection."
- Simpler sync (no conflict resolution for the contested resource).

## Alternatives considered
- **Offline claim queue with server reconciliation.** Rejected — cannot preserve first-claim-wins; reintroduces the exact problem the product solves.
- **Fully online app.** Rejected — wastes the offline-read value drivers need on the road.

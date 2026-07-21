# ADR-0008: Realtime via WebSocket + Redis pub/sub; FCM for push only

**Status:** Accepted · 2026-07-21

## Context
Live per-leg status ("this leg was just claimed") is core UX. The prototype used Firestore snapshot listeners; the archived web demo used SSE. With a backend now owning the domain (ADR-0002) and Firestore retired for data (ADR-0004), we need a realtime transport that the backend controls, scales fan-out, and scopes to relevance (area/vehicle/time) rather than streaming the whole feed to every driver.

## Decision
- **Live status:** authenticated **WebSocket (WSS)**; server fans out deltas via **Redis pub/sub**, scoped by the driver's subscriptions. Clients reconcile with a REST snapshot on connect, then apply deltas.
- **Push notifications:** **FCM** only — waking the device / background alerts for subscribed areas. FCM is not a data channel.

## Consequences
- Backend controls fan-out volume and relevance → cost scales with active subscriptions, not (drivers × all legs).
- One realtime contract across clients.
- We operate WS infrastructure + Redis; need reconnect/backoff and delta reconciliation logic on clients.

## Alternatives considered
- **Keep Firestore listeners.** Contradicts ADR-0004; cost/relevance/geo limits at scale. Rejected.
- **SSE (as in the prototype).** Simpler but one-directional and weaker for scoped subscriptions + client→server liveness. Acceptable fallback; WS chosen for bidirectionality and subscription control.
- **Long-polling.** Rejected (latency/cost).

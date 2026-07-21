# ARCHIVED — do not extend

This web client (`index.html` + `server.js`) was a **throwaway prototype** built
to validate the dispatch workflow: structured multi-leg posting, server-authoritative
first-claim-wins locking, and realtime status sync across clients.

**It succeeded at that and is now frozen.** No further engineering effort goes here.

- It is **not** the production backend and shares no code with it.
- Do not add features, auth, persistence, or payments to it.
- Reopen this only if an **Admin Portal / Web client** is explicitly requested,
  and even then, build against the real ITCABS backend API — not `server.js`.

The production system is: the Android app (`/app`) + the backend service
(see `docs/architecture/PRODUCTION_ARCHITECTURE.md`).

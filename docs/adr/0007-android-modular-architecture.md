# ADR-0007: Android multi-module, offline-first architecture

**Status:** Accepted · 2026-07-21

## Context
The current app is a single `:app` module with a god-object `Repo`, one catch-all `AppViewModel`, Firestore-coupled models, and no UI-state modeling or DI. It cannot scale in team size or feature count, and it must be re-platformed onto the backend API (ADR-0002).

## Decision
Adopt a **multi-module, layered, offline-first** architecture modeled on Google's "Now in Android":
- Modules: `:app`, `:core:{designsystem,common,network,database,datastore}`, `:domain` (pure), `:data`, `:feature:*`.
- **Hilt** for DI.
- **Retrofit + OkHttp + kotlinx.serialization** for network; **Room** as the single source of truth; encrypted DataStore for tokens.
- **UDF**: each feature ViewModel exposes an immutable `UiState`; events in, state out.
- `:domain` holds framework-free entities + use cases; `:data` holds DTOs, mappers, repository implementations.

## Consequences
- Clear seams → testable, parallelizable, feature-ownable.
- Domain is portable and framework-free (no Firestore annotations).
- Higher upfront structure cost (justified: maintainability over speed, per directive).
- Realtime WS deltas land in Room; UI observes Room and updates automatically.

## Alternatives considered
- **Keep single-module MVVM.** Lowest effort; fails the maintainability/scale goal. Rejected.
- **MVI framework (e.g. Orbit/Mavericks).** Fine, but plain UDF with Compose + coroutines is sufficient and dependency-light. Rejected to avoid an extra framework.

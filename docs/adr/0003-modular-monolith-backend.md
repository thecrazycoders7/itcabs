# ADR-0003: Backend as a Kotlin/Spring Boot modular monolith

**Status:** Accepted · 2026-07-21

## Context
Given ADR-0002, we must choose the backend's language, framework, and macro-structure. Constraints: maintainability over speed, India scale, a team already writing Kotlin (Android), and future service extraction if needed.

## Decision
Build the backend in **Kotlin + Spring Boot 3** as a **modular monolith** — one deployable, with enforced module boundaries per bounded context (`identity`, `dispatch`, `discovery`, `reputation`, `payments`, `trust-safety`, `notifications`, `shared`). Contexts communicate via interfaces and in-process domain events, so any can be extracted into its own service later without rewriting callers.

## Consequences
- **Kotlin both sides:** shared mental model and potential shared DTO/domain definitions; one hiring profile.
- **Spring Boot:** mature, batteries-included (security, validation, data, observability), large ecosystem — the boring-correct choice for maintainability.
- **Modular monolith:** avoids premature microservice complexity (distributed transactions, ops overhead) while keeping clean seams. Single transactional boundary makes the claim invariant simple.
- Requires discipline to keep module boundaries from eroding (enforced via module structure + architecture tests, e.g. Konsist/ArchUnit).

## Alternatives considered
- **Microservices now.** Rejected — premature; the domain isn't large enough to justify distributed-systems tax; contention on the claim would span services.
- **Ktor.** Lighter and idiomatic Kotlin, but less batteries-included; more glue to write for security/data/validation. Reasonable second choice; rejected for maintainability/ecosystem depth.
- **Node/NestJS or Go.** Good options, but a second language and no domain-sharing with Android. Rejected.

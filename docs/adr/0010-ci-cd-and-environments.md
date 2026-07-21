# ADR-0010: CI/CD, environments, and quality gates

**Status:** Accepted · 2026-07-21

## Context
Today there is no CI, no static analysis, one test, and no environments or distribution pipeline. For a production platform with two codebases (Android + backend) and future clients, quality must be gated automatically and releases must be repeatable.

## Decision
- **Environments:** `local → dev → staging → prod`, config via environment/secret store (never in repo).
- **CI (GitHub Actions), per PR, on both repos:** ktlint + detekt (Android) / ktlint + detekt (backend) → unit tests → integration tests (Testcontainers Postgres for backend) → build/assemble. `main` is protected; green CI required to merge.
- **CD:** backend containerized and deployed per environment on merge to `main`; **Android** to Play **internal → closed → production** tracks (and/or Firebase App Distribution for QA).
- **Later:** instrumentation tests on an emulator matrix; contract tests against the published OpenAPI; load tests in staging gating perf targets.

## Consequences
- Regressions caught before merge; releases are reproducible and auditable.
- Some pipeline maintenance overhead; justified by the reliability bar.
- Requires signing-key and secret management from the start.

## Alternatives considered
- **Manual builds/tests.** Rejected — does not scale to a team or a production SLA.
- **Other CI (GitLab/CircleCI/Bitrise).** Any is fine; GitHub Actions chosen assuming GitHub hosting (see memory: repos under the `harshanulfinity` GitHub account). Revisit if hosting changes.

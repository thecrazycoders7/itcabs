# ADR-0001: Record architecture decisions

**Status:** Accepted · 2026-07-21

## Context
ITCABS is moving from a validated prototype to a production platform intended to serve thousands of coordinators and hundreds of thousands of drivers, with future iOS/Web/Admin clients. Major decisions must be traceable so future contributors (and future clients) understand *why*, not just *what*.

## Decision
We record every major architectural decision as an ADR in `docs/adr/`, numbered sequentially, using the format: **Status, Context, Decision, Consequences, Alternatives considered.** A decision is "major" if it is expensive to reverse, constrains multiple modules, or affects the public API/contract, security, or compliance.

## Consequences
- Small overhead per decision; large payoff in onboarding and in avoiding re-litigation.
- Superseded ADRs are marked `Superseded by ADR-XXXX`, never deleted.

## Alternatives considered
- Tribal knowledge / commit messages — not discoverable, not durable. Rejected.

# M0 deferred (accepted 2026-07-21)

User chose to jump to M1 (backend) before doing M0. These items on the **current
Android app** remain OPEN and must be done before that app ships or is re-platformed:

- **P0 — Plaintext PII:** `UserProfile.aadhaar` / `rcNumber` still stored raw in Firestore. Compliance risk (Aadhaar Act, DPDP 2023). See ADR-0006.
- **P2 — Serialized computed getters:** `ratingAvg`, `kycComplete` in `app/.../Models.kt` serialize to Firestore on write. Add `@get:Exclude` or obsolete via domain/DTO split.
- **P2 — Dead code:** `Repo.newRequestId()` + unused `java.util.UUID` import.
- **P2 — Version catalog:** introduce `libs.versions.toml`.
- **P1 — CI:** no ktlint/detekt/test gate on the Android app.

Note: M1 makes the Firebase-direct app obsolete anyway (backend owns the domain),
so some of these die naturally during the Android re-platform (M2). PII is the one
that cannot wait if the current app touches any real user data.

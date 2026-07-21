# ADR-0006: PII handling & compliance (Aadhaar, KYC, DPDP)

**Status:** Accepted · 2026-07-21

## Context
Driver verification collects Aadhaar number, RC number, and document images. The current model stores Aadhaar and RC as **plaintext string fields** in Firestore. This is a serious legal exposure: the Aadhaar Act restricts storage of Aadhaar numbers, and the DPDP Act 2023 imposes consent, minimization, retention, and erasure obligations. This is the highest-severity finding in the audit (F4).

## Decision
- **Never store the raw Aadhaar number.** Store `aadhaar_ref` (a token from the KYC/verification provider or an internal vault) + `aadhaar_masked` (`XXXX-XXXX-1234`) only.
- Verify Aadhaar/RC through a licensed KYC/verification API rather than holding raw identifiers.
- KYC **document images** live in scoped object storage (signed, time-limited access), not public URLs.
- **Encryption at rest** for all PII; **audit log** for access to and changes of verification/PII.
- **Consent capture** at KYC submission; documented **retention** and **erasure** support (DPDP).

## Consequences
- Compliance risk sharply reduced; the raw Aadhaar number never rests in our datastore.
- Requires a KYC/verification vendor integration and a secrets/vault story.
- The current app's `photoUrl`/`aadhaar` fields are replaced by references; the Android KYC form changes accordingly.

## Alternatives considered
- **Store raw, encrypted.** Still storage of the number; higher legal exposure than tokenization. Rejected.
- **Skip Aadhaar entirely, use only RC + selfie.** Reduces exposure but the product brief requires Aadhaar-based driver identity. Kept Aadhaar but tokenized.

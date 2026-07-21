# ADR-0005: Backend-owned authentication (phone OTP + JWT)

**Status:** Accepted · 2026-07-21

## Context
The prototype uses Firebase Phone Auth; the session is `FirebaseAuth.currentUser`. That couples every future client to Firebase Auth, gives us no server-owned session, no refresh/rotation control, no OTP rate-limiting, and no admin-approval hook for driver verification. Auth also needs to be identical across Android/iOS/Web/Admin.

## Decision
The backend owns authentication. Phone **OTP via an SMS gateway** (e.g. MSG91), backend issues **JWT access (short-lived) + refresh (long-lived, rotating)** tokens, sessions tracked in `device_sessions` (revocable). OTP requests are rate-limited per phone/IP. Driver **verification is an admin-approval workflow** on `kyc_status`, never a client-settable flag.

## Consequences
- Portable across all clients; one auth contract.
- We control token lifetime, rotation, revocation, and multi-device sessions.
- OTP abuse/cost controlled via rate limits.
- We operate an SMS integration and secure signing keys.

## Alternatives considered
- **Firebase Auth (keep).** Easy and battle-tested, but couples clients to Firebase and gives us no server session ownership. Rejected for the multi-client/portability goal (could be reconsidered as the OTP delivery mechanism only).
- **Third-party IdP (Auth0/Cognito).** Viable; adds cost/vendor coupling and less control over the India phone-OTP UX. Rejected for now.

# itcabs

Production-grade transport-dispatch platform (Android now; iOS/Web/Admin later)
replacing the free-text Telegram group IT-transport coordinators use to arrange
employee shuttle rides.

> **Read [`docs/`](docs/README.md) first** — the architecture audit, production
> architecture, roadmaps, and ADRs. The project is in a **refactor-before-features**
> phase: the current code below is the validated MVP scaffold being re-platformed
> onto a server-owned backend. Do not add features ahead of the refactoring roadmap.

## Current code (MVP scaffold — Firebase, single module)

The Android app below is the **core loop** that validated the workflow. Payments,
masked calling, push, and templates are deferred; the target architecture in
`docs/` supersedes this Firebase-direct design.

## What it does

Two roles, chosen on first sign-in (phone-OTP verified):

- **Coordinator** — posts a *requirement* = office + shift + one or more **legs**
  (pickup → drop, area, time window, vehicle type, fare, seats). Sees a live
  dashboard of every leg with per-leg status (open → claimed → confirmed →
  completed) and rates the driver after completion.
- **Driver** — fills mandatory KYC (name, vehicle type, registration no.,
  Aadhaar no., RC no., photo). Stays hidden and cannot claim until a supervisor
  flips `verified`. Once verified: browse/filter open legs by area + vehicle and
  **claim one leg with one tap**.

**First-claim-wins** is a Firestore transaction (`Repo.claimLeg`): it reads the
leg, and only moves `OPEN → CLAIMED` if it is still open — a second concurrent
claim loses. `firestore.rules` enforces the same transition server-side, plus
the verification gate and the block flag, so a hacked client can't cheat.

## Data model (Firestore)

```
users/{uid}                role, name, phone, KYC fields, verified, blocked, rating
jobs/{jobId}               coordinatorId, office, shift, createdAt
jobs/{jobId}/legs/{legId}  pickup, drop, area, timeWindow, vehicleType, fare,
                           seats, status, claimedBy, ...
```

Legs are their own docs so a claim is a single-doc transaction and drivers query
open legs across all jobs with a `collectionGroup("legs")` query.

## Setup (you need Android Studio — it can't be built on this machine)

1. Create a Firebase project. Enable **Authentication → Phone**, **Firestore**,
   and **Storage**.
2. Add an Android app with package `com.itcabs`, download `google-services.json`
   into `app/`.
3. Deploy the rules: `firebase deploy --only firestore:rules` (or paste
   `firestore.rules` in the console).
4. Firestore needs composite indexes for the collectionGroup queries — the first
   run logs a console link that creates them (status+area+vehicleType,
   coordinatorId, claimedBy).
5. Open the folder in Android Studio, let it provision the Gradle wrapper, Run.
6. **Verifying a driver / blocking a fraudster** is a backend/admin action:
   flip `verified` or `blocked` on the `users` doc (Firebase console for now).
   Because the doc id is the phone-verified uid, a blocked driver can't reappear
   without a new number.

## Test

`./gradlew test` — `ClaimTest` proves the first-claim-wins guard (50 concurrent
claimers, exactly one wins).

## Not built yet (deferred from the full spec)

- In-app payment / receipt flow (fare is displayed & fixed; settlement is manual)
- Masked call / in-app chat channel
- FCM push for area/time-window subscriptions
- Recurring / templated postings
- Driver photo capture + Storage upload wired to a real image picker
  (`Repo.uploadPhoto` exists; the KYC form currently takes a photo URL)
- Admin console for verify/block (done via Firebase console today)

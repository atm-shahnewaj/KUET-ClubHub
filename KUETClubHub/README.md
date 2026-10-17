# KUET ClubHub

Android app (Java, XML + view binding, Firebase Auth + Firestore, Room) for clubs and campus events at KUET.
Built in weekly parts following the project proposal.

## Week 1
Project setup, Firebase connection, email/password auth with Student / Club organizer / Admin roles,
role-based bottom navigation, club directory (search + category filter), Room offline cache,
club create/edit, admin approval.

## Setup
1. Replace `app/google-services.json` with the file from your Firebase project
   (Android package name must be `com.kuet.clubhub`).
2. In Firebase Console enable Authentication -> Email/Password and create a Firestore database.
3. Publish `firestore.rules` (Firestore -> Rules).
4. Open this folder in Android Studio, let Gradle sync, run on a device.

## Roles
- `student` and `organizer` can be chosen at sign-up.
- `admin` is granted only by editing the user's `role` field to `admin` in the Firestore console.

## Debugging lifecycle
Filter Logcat by `ClubHubLifecycle` to see every Activity lifecycle callback.

## Week 1 polish (0.1.1)
- Fixed: Clubs tab was blank on first open (first tab is pre-selected, so it never loaded).
- KUET logo as launcher icon and in-app branding; palette taken from the logo.
- Light / dark / system appearance: toolbar button (quick flip) and Profile -> Appearance.

## Week 2 (0.2.0)
Events (workshop / seminar / competition / other), event list with search + type filter, hand-built month
calendar view with coloured dots, event detail, registration with seat capacity enforced by a Firestore
transaction, waiting list with automatic promotion when someone cancels, "My events", organizer/admin
event create/edit and a participants list. Room now also caches events for offline use.
Publish the updated `firestore.rules` (it now covers events, registrations and the waiting list).

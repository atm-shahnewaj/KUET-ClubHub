# KUET ClubHub - Week 5

Plan for this week: **team formation and matcher, membership applications, feedback, PDF certificate with verification.**

## Before you run it: publish the new rules
Week 5 adds new collections, so `firestore.rules` **must be re-published** (Firebase Console -> Firestore Database -> Rules -> paste -> Publish). Without it, every new feature shows "Not allowed". No new indexes are needed (all queries filter on a single field).

## What is new

### 1. Club membership applications (feature 12)
- Club page: **Apply to join** (optional message), then *Application pending* / *You are a member* / *Application declined (apply again)*. Withdraw and Leave are supported.
- Organizers (and admins) get **Membership requests**: a Pending / Members screen with Approve, Reject and Remove.
- Data: `clubs/{clubId}/members/{uid}` (status pending | approved | rejected). Approving, removing and leaving move `memberCount` in the same batch; the rules only let a member lower the count by exactly one while deleting their own member document.

### 2. Teams and the matcher (features 10, 23, 24, 25)
Open a competition you are registered for -> **Find or build a team**. Three tabs:
- **Teams**: ranked by how well *your* skills fit what each team asks for (explained: "Fits you: Covers java, design"). Open teams have a Join button.
- **People** (the looking-for-teammates board): post your skills; team leaders see ranked people with **Invite**.
- **Invites**: accept or decline.

Team screen: members and skills, status ("Ready to compete" / "Needs 1 more"), **Invite code and QR** (share as text, or pick a phone contact and send an SMS), edit, disband, leave, remove a member, and **Suggested teammates** for the leader.
- Join by typing the 8-character code, scanning the team QR, joining an open team, or accepting an invite.
- Team size limits come from the event. The last place cannot be taken twice (transaction), and a student cannot be in two teams of one event (see data model).
- **Matcher** (`TeamMatcher`, pure Java): +10 for each skill the team asked for that the person has, +2 for each extra skill nobody in the team has yet. Unit tests in `app/src/test`.
- Contacts: uses the system contact picker (`ACTION_PICK` on phone numbers). It returns just the one picked number, so **no READ_CONTACTS permission is needed**.

### 3. Feedback (feature 11)
After check-in, the event page shows **Rate this event** (1-5 stars + comment, editable). Organizers get **View feedback** (average + every comment). The rules accept feedback only if the student's check-in record exists.

### 4. PDF certificate and verification (features 28, 29)
- After an event has ended, a checked-in student gets **Get my certificate**: issued once, drawn on the phone onto a bundled template (logo, name, event, club, date, ID, QR), shown as a preview and saved as a PDF. **Open PDF** and **Share or save PDF** use implicit Intents through a `FileProvider`.
- Bangla names use a bundled Noto Sans Bengali font (SIL OFL) for consistent rendering.
- **Profile -> Verify a certificate**: type the ID (`KCH-XXXX-XXXX`) or scan the QR. The result comes from `certificates/{certId}`.
- The rules only allow a certificate for a checked-in student, after the event ended, and require the copied name, event title, club and date to equal the real documents, so a student cannot forge one.

## Data model added
```
clubs/{clubId}/members/{uid}            status, name, studentId, department, year, message, appliedAt, decidedAt
events/{eventId}/feedback/{uid}         rating, comment, name, updatedAt
events/{eventId}/seekers/{uid}          the looking-for-teammates board
events/{eventId}/teamMembers/{uid}      teamId, via   (create-only: one team per student per event)
events/{eventId}/certIssued/{uid}       certId        (create-only: one certificate per student per event)
teams/{teamId}                          eventId, name, leaderUid, memberUids[], memberNames{}, memberSkills{},
                                        skillsNeeded[], note, open, minSize, maxSize
teams/{teamId}/private/invite           code (members only)
inviteCodes/{code}                      teamId, eventId (get only, never listable)
users/{uid}/teamInvites/{teamId}        invitations
certificates/{certId}                   eventId, uid, type, name, eventTitle, clubName, eventDate, issuedAt
```
Small differences from the proposal's sketch: feedback is a subcollection of the event (simpler, safer rules than a top-level `feedback/{id}`); `skillsNeeded[]` is used instead of `rolesNeeded[]` (role slots are a stretch feature); the invite code lives in a members-only document so it cannot be read from the board.

## How to test (two student accounts + one organizer)
1. **Membership**: student applies -> organizer approves (count +1) -> student leaves (count -1).
2. **Teams**: create a competition with team size e.g. 2-3 and a start time in the future. Register student A and B. A creates a team (add skills needed), B joins by code, QR, open join, and invite (on the People tab). Fill the team and check that a further join is refused. Leave / remove / disband.
3. **Feedback**: organizer checks a student in (manual is fine), student rates, organizer opens View feedback.
4. **Certificate**: needs a *checked-in student* and an event whose **end time has passed** (edit the event's times after checking in). Open the certificate, open the PDF, then verify it by ID and by scanning the QR from another phone/account.

## Not verified yet - please read
- I could not build the app or run it on a device/emulator in this environment (no Android SDK or Maven access). The Java was syntax-checked and cross-checked automatically (all `R.*`, view-binding fields and project imports resolve), and `TeamMatcher` was compiled and run on its own, but **a Gradle build and a real-device run are still needed**. If the build reports an error, send it to me and I will fix it.
- The new Firestore rules were not run in the Rules Playground/emulator. Please try the test flow above after publishing them.
- The PDF/Bangla rendering and the share/SMS/contact-picker intents depend on the phone. Test a long English name, a short name and a Bangla name on a real phone (as listed in the proposal's test plan).

## Known limits (by design, for now)
- Teams can only be created, joined or changed **before the event starts**.
- The team leader cannot leave; they can disband (leader transfer is not built).
- Skills are entered as tags, so a multi-word skill such as "machine learning" becomes two tags.
- Invitations that were already sent stay in the invitee's list after a team is disbanded; accepting one says the team no longer exists.
- Only the participation certificate type exists (winner certificates would be a small extension).
- Not done this week, per the plan: polls and Q&A, analytics/CSV, calendar and weather integration, widget (Week 6).

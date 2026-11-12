# KUET ClubHub - Week 6 (Complete): Polls & Q&A, Analytics & CSV Export, Calendar, Weather, Countdown, Widget & Shortcuts

This build completes the entire **Week 6** plan:
1. **Live polls & Q&A board** (Proposal features 13 and 14)
2. **Analytics charts, no-show tracking, and CSV participant export** (Proposal features 12, 33, 34)
3. **Clone event** (Proposal feature 35)
4. **Live event countdown, Add to phone calendar (`CalendarContract`), and Campus weather forecast (`HttpURLConnection` + `org.json`)** (Proposal features 17, 21, 39)
5. **Home screen widget (`AppWidgetProvider`) and App shortcuts** (Proposal features 17, 41, 42)

## Before you run it: publish the rules
`firestore.rules` includes the `polls`, `pollVotes`, and `questions` rules added in Week 6 Part 1. None of the Week 6 Part 2 features require new Firestore collections or indexes (`Participant.updatedAt` was already written by `EventRepository.register()`). Make sure the current `firestore.rules` is published in **Firebase Console -> Firestore Database -> Rules -> Publish**.

---

## What is in Week 6

### 1. Live polls and Q&A (features 13, 14)
- **Where to find it**: Event page -> **Polls & Q&A** (`LiveActivity`, two tabs: **Polls** and **Q&A**).
- **Live polls**: Organizers launch polls (question + 2 to 4 options), close/reopen voting, or delete. Checked-in students vote once (enforced by a batch write creating `pollVotes/{pollId}_{uid}` and incrementing `counts[option]` by 1) and see a live animated horizontal bar chart (`PollBarsView`).
- **Live Q&A**: Checked-in students ask questions (optionally anonymously) and upvote; `QuestionRanker` orders unanswered questions by upvotes (earlier first on ties) and sinks answered questions to the bottom.

### 2. Organizer analytics, no-show tracking, and CSV export (features 12, 33, 34)
- **Where to find it**:
  - Organizer Dashboard (`EventDashboardActivity`) -> **Analytics** and **Export CSV** buttons right below the check-in buttons.
  - Event page (`EventDetailActivity`, organizer block) -> **Analytics & CSV export**.
- **Analytics screen (`EventAnalyticsActivity`)**:
  - **Attendance vs. registration**: shows seats filled %, attendance rate %, and animated horizontal bars (`PollBarsView`) for Registered, Checked in, Not yet here / **No-shows** (once the event has ended), and Waiting.
  - **Registrations over time**: groups active registrations by local calendar day (`Participant.updatedAt`) in chronological order (`EventAnalytics.registrationsByDay`).
  - **Department breakdown**: groups active participants by department (`CSE`, `EEE`, `ME`, etc.) sorted from largest to smallest (`EventAnalytics.departmentBreakdown`).
- **CSV export (`CsvExporter`)**:
  - Writes an RFC-4180 CSV (`Name,Student ID,Department,Status,Attendance,Checked In At,Registered At`) to `files/exports/` and shares it via `FileProvider` and an implicit `Intent.ACTION_SEND` chooser (`text/csv`).
  - Registered students who did not check in are flagged as `no_show` once the event has ended (`not_yet` beforehand).

### 3. Live countdown, Campus weather forecast, Add to phone calendar, and Clone event (features 17, 21, 35, 39)
- **Live countdown (`CountdownText`)**:
  - Inside the **Details** card on `EventDetailActivity`, the **Countdown** row ticks every second while the screen is visible (`Starts in 2d 04h 15m 08s`, `Happening now · 42m 10s left`, or `Event ended`).
- **Campus weather forecast (`WeatherRepository`, `WeatherParser`)**:
  - Shown on `EventDetailActivity` for upcoming events in the **Campus weather (KUET)** card.
  - Queries the free Open-Meteo HTTP JSON API for KUET campus (`22.9006°N, 89.5024°E`, no API key required) on a background network thread (`Io.NETWORK`), with a 15-minute in-memory cache and loading, content, and error + **Try again** states.
- **Add to phone calendar (`CalendarHelper`)**:
  - On `EventDetailActivity`, tapping **Add to phone calendar** requests `READ_CALENDAR` / `WRITE_CALENDAR`.
  - When granted, it queries `CalendarContract.Calendars.CONTENT_URI` via `ContentResolver` for a writable calendar and inserts the event directly into `CalendarContract.Events.CONTENT_URI` (preventing duplicates if tapped twice).
  - If the permission is denied (or the phone has no synced local calendar account), it degrades gracefully by opening the system calendar app's pre-filled insert screen via `Intent.ACTION_INSERT`.
- **Clone event (`EventEditActivity.EXTRA_CLONE_FROM_ID`)**:
  - Organizers can tap **Clone event** in the organizer block on `EventDetailActivity` to pre-fill a new event with all details, tags, capacity, and competition settings from an existing event while picking new dates.

### 4. Home screen widget and App shortcuts (features 17, 41, 42)
- **Home screen widget (`NextEventWidgetProvider`)**:
  - Shows the signed-in student's next upcoming registered event from Room (`ScheduleDao` + `WidgetEventPicker`) with a compact countdown badge (`In 2d 4h`, `Live now`, etc.), date/time, and venue.
  - Uses pure `RemoteViews`-safe views and drawables (`widget_next_event.xml`, `bg_widget_card.xml`, `bg_widget_pill.xml`) so it renders reliably across launchers in light and dark mode.
  - Updates automatically whenever `ScheduleSync` updates or clears the local schedule cache. Tapping the widget opens that event's detail page (or the app if no upcoming event is registered).
- **App shortcuts (`res/xml/shortcuts.xml`)**:
  - Long-pressing the KUET ClubHub launcher icon offers **My ticket** (opens the next upcoming registered event's QR pass in `EventPassActivity`) and **Scan QR** (opens `ScanActivity` in student check-in mode for the next upcoming registered event).

### 5. Unit tests (`app/src/test`)
- `TeamMatcherTest` (Week 5)
- `PollMathTest` and `QuestionRankerTest` (Week 6 Part 1)
- `EventAnalyticsTest`, `CsvExporterTest`, `CountdownTextTest`, `WeatherParserTest`, and `WidgetEventPickerTest` (Week 6 Part 2 — plain JUnit so `./gradlew test` runs cleanly; `WeatherParser` uses `org.json`, supplied to the unit tests through the `org.json:json` test dependency).

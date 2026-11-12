package com.kuet.clubhub.notify;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.data.CheckInRepository;
import com.kuet.clubhub.data.ClubChatRepository;
import com.kuet.clubhub.data.ClubNoticeRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.LiveRepository;
import com.kuet.clubhub.data.MembershipRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.TeamRepository;
import com.kuet.clubhub.model.ChatMessage;
import com.kuet.clubhub.model.CheckIn;
import com.kuet.clubhub.model.Club;
import com.kuet.clubhub.model.ClubMember;
import com.kuet.clubhub.model.ClubNotice;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.EventType;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Poll;
import com.kuet.clubhub.model.Question;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.Seeker;
import com.kuet.clubhub.model.Team;
import com.kuet.clubhub.model.TeamInvite;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.NotificationPrefs;
import com.kuet.clubhub.util.TimeFormats;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Watches real-time Firestore & Realtime Database streams for the signed-in user and triggers
 * immediate {@link ToastCenter} toasts (plus notifications) across all 12 requested scenarios:
 *
 * 1. Waitlist promoted: “A spot opened. You’re now registered for <event>.”
 * 2. Registration confirmed: After registering, confirm with optional QR pass code.
 * 3. Club join approved or rejected: To the student.
 * 4. New join request: To club organizers.
 * 5. Event updated or cancelled: If time, venue or status changes after registering.
 * 6. New club notice or pinned notice: To members of that club.
 * 7. Group chat messages or mentions: Notifications/toasts for club chat with per-club mute switch.
 * 8. Team invites and join requests: Real-time toasts for team invites, new members, and seekers.
 * 9. Certificate ready: “Your certificate for <event> is available.”
 * 10. Live poll or Q&A started: For people currently registered or checked in.
 * 11. Feedback prompt: After an event ends: “How was <event>?”
 * 12. Event starting, check-in open: For organizers and attendees.
 */
public final class RealtimeToastWatcher {

    private static final String PREFS = "clubhub_toast_watcher_state";
    private static final long TICK_INTERVAL_MS = 30_000L;
    private static final long EVENT_STARTING_SOON_WINDOW_MS = 15 * 60 * 1_000L;

    private static RealtimeToastWatcher instance;

    private final Context app;
    private final SharedPreferences statePrefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String activeUid;
    private UserProfile activeProfile;
    private int activeActivityCount = 0;

    // Top-level listeners
    private ListenerRegistration myRegsListener;
    private ListenerRegistration clubsListener;
    private ListenerRegistration teamInvitesListener;

    // Per-event listeners (for events the user is registered for or organizes)
    private final Map<String, ListenerRegistration> eventDocListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> eventPollsListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> eventQuestionsListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> eventCheckInMyListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> eventTokenListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> eventTeamsListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> eventSeekersListeners = new HashMap<>();

    // Per-club listeners
    private final Map<String, ListenerRegistration> clubMyMembershipListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> clubAllMembersListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> clubNoticesListeners = new HashMap<>();
    private final Map<String, ClubChatRepository.Handle> clubChatHandles = new HashMap<>();

    // In-memory state snapshots to detect live transitions without firing on initial attach
    private final Map<String, String> regStatusMemory = new HashMap<>();
    private final Map<String, EventSnapshot> eventMemory = new HashMap<>();
    private final Map<String, Boolean> checkInMemory = new HashMap<>();
    private final Map<String, Set<String>> knownPollsMemory = new HashMap<>();
    private final Map<String, Set<String>> knownQuestionsMemory = new HashMap<>();
    private final Map<String, Set<String>> knownTeamMembersMemory = new HashMap<>();
    private final Map<String, Set<String>> knownSeekersMemory = new HashMap<>();
    private final Set<String> knownInvitesMemory = new HashSet<>();
    private boolean invitesInitialized = false;

    private final Map<String, String> clubMyStatusMemory = new HashMap<>();
    private final Map<String, Set<String>> clubPendingUidsMemory = new HashMap<>();
    private final Map<String, Set<String>> clubNoticeIdsMemory = new HashMap<>();
    private final Map<String, Set<String>> clubChatIdsMemory = new HashMap<>();
    private final Map<String, Club> knownClubs = new HashMap<>();
    private final Set<String> memberOrOrganizerClubIds = new HashSet<>();

    /** Club ID currently open in ClubChatActivity so we don't duplicate toasts while chatting in it. */
    @Nullable
    private String activeOpenChatClubId;

    private final Runnable timeTransitionsTicker = new Runnable() {
        @Override
        public void run() {
            checkTimeBasedTransitions();
            if (activeActivityCount > 0 && activeUid != null) {
                handler.postDelayed(this, TICK_INTERVAL_MS);
            }
        }
    };

    private RealtimeToastWatcher(Context context) {
        this.app = context.getApplicationContext();
        this.statePrefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized RealtimeToastWatcher getInstance(Context context) {
        if (instance == null) {
            instance = new RealtimeToastWatcher(context);
        }
        return instance;
    }

    public void setActiveOpenChatClubId(@Nullable String clubId) {
        this.activeOpenChatClubId = clubId;
    }

    /** Called from BaseActivity.onStart(). */
    public void onActivityStarted(Context context) {
        activeActivityCount++;
        ensureWatching();
        handler.removeCallbacks(timeTransitionsTicker);
        handler.postDelayed(timeTransitionsTicker, 2_500L);
    }

    /** Called from BaseActivity.onStop(). */
    public void onActivityStopped() {
        activeActivityCount = Math.max(0, activeActivityCount - 1);
        if (activeActivityCount == 0) {
            handler.removeCallbacks(timeTransitionsTicker);
        }
    }

    /** Ensures listeners are attached for the currently signed-in user. */
    public void ensureWatching() {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        UserProfile profile = new SessionManager(app).getProfile();
        if (fbUser == null || profile == null || profile.getUid() == null) {
            stopAll();
            return;
        }
        String uid = profile.getUid();
        activeProfile = profile;
        if (!uid.equals(activeUid)) {
            stopAll();
            activeUid = uid;
            startTopLevelListeners(uid);
        }
    }

    public void stopAll() {
        handler.removeCallbacks(timeTransitionsTicker);
        activeUid = null;
        activeProfile = null;

        if (myRegsListener != null) {
            myRegsListener.remove();
            myRegsListener = null;
        }
        if (clubsListener != null) {
            clubsListener.remove();
            clubsListener = null;
        }
        if (teamInvitesListener != null) {
            teamInvitesListener.remove();
            teamInvitesListener = null;
        }

        clearRegistrationMap(eventDocListeners);
        clearRegistrationMap(eventPollsListeners);
        clearRegistrationMap(eventQuestionsListeners);
        clearRegistrationMap(eventCheckInMyListeners);
        clearRegistrationMap(eventTokenListeners);
        clearRegistrationMap(eventTeamsListeners);
        clearRegistrationMap(eventSeekersListeners);

        clearRegistrationMap(clubMyMembershipListeners);
        clearRegistrationMap(clubAllMembersListeners);
        clearRegistrationMap(clubNoticesListeners);

        for (ClubChatRepository.Handle h : clubChatHandles.values()) {
            if (h != null) {
                h.remove();
            }
        }
        clubChatHandles.clear();

        regStatusMemory.clear();
        eventMemory.clear();
        checkInMemory.clear();
        knownPollsMemory.clear();
        knownQuestionsMemory.clear();
        knownTeamMembersMemory.clear();
        knownSeekersMemory.clear();
        knownInvitesMemory.clear();
        invitesInitialized = false;

        clubMyStatusMemory.clear();
        clubPendingUidsMemory.clear();
        clubNoticeIdsMemory.clear();
        clubChatIdsMemory.clear();
        knownClubs.clear();
        memberOrOrganizerClubIds.clear();
    }

    private static void clearRegistrationMap(Map<String, ListenerRegistration> map) {
        for (ListenerRegistration r : map.values()) {
            if (r != null) {
                r.remove();
            }
        }
        map.clear();
    }

    // ------------------------------------------------------------------ Top-level Listeners

    private void startTopLevelListeners(@NonNull final String uid) {
        // 1. Watch user's registrations (Waitlist promoted, Registered events, Polls/Q&A, Certs, Feedback)
        myRegsListener = EventRepository.getInstance(app).listenMyRegistrations(uid,
                new EventRepository.MyRegistrationsListener() {
                    @Override
                    public void onChanged(List<MyRegistration> registrations) {
                        if (!uid.equals(activeUid)) {
                            return;
                        }
                        handleMyRegistrationsSnapshot(uid, registrations);
                    }

                    @Override
                    public void onError(String message) {
                    }
                });

        // 2. Watch team invites sent to this user
        teamInvitesListener = TeamRepository.listenInvites(uid, new TeamRepository.InvitesListener() {
            @Override
            public void onInvites(List<TeamInvite> invites) {
                if (!uid.equals(activeUid)) {
                    return;
                }
                handleTeamInvitesSnapshot(uid, invites);
            }

            @Override
            public void onError(String message) {
            }
        });

        // 3. Watch clubs (to attach membership decision listeners, organizer join-request listeners,
        //    club notice listeners, and club chat listeners)
        clubsListener = FirebaseFirestore.getInstance().collection("clubs")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null || !uid.equals(activeUid)) {
                        return;
                    }
                    List<Club> clubs = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Club c = d.toObject(Club.class);
                        if (c != null) {
                            c.setId(d.getId());
                            clubs.add(c);
                        }
                    }
                    handleClubsSnapshot(uid, clubs);
                });
    }

    // ------------------------------------------------------------------ 1, 5, 9, 10, 11, 12: Events & Registrations

    private void handleMyRegistrationsSnapshot(@NonNull String uid,
                                               @NonNull List<MyRegistration> registrations) {
        Set<String> activeEventIds = new HashSet<>();
        for (MyRegistration reg : registrations) {
            if (reg == null || reg.getEventId() == null) {
                continue;
            }
            String eventId = reg.getEventId();
            String newStatus = reg.getStatus() == null ? "" : reg.getStatus();
            String prevStatus = regStatusMemory.get(eventId);
            String persistedKey = "reg_status_" + uid + "_" + eventId;
            String persistedStatus = statePrefs.getString(persistedKey, null);

            // Check for Waitlist -> Registered promotion!
            boolean promotedInMemory = MyRegistration.WAITLISTED.equals(prevStatus)
                    && MyRegistration.REGISTERED.equals(newStatus);
            boolean promotedSinceLastSession = prevStatus == null
                    && MyRegistration.WAITLISTED.equals(persistedStatus)
                    && MyRegistration.REGISTERED.equals(newStatus);

            if (promotedInMemory || promotedSinceLastSession) {
                ToastCenter.waitlistPromoted(app, reg.getTitle(), eventId);
            }

            regStatusMemory.put(eventId, newStatus);
            statePrefs.edit().putString(persistedKey, newStatus).apply();

            if (MyRegistration.REGISTERED.equals(newStatus)
                    || MyRegistration.WAITLISTED.equals(newStatus)) {
                activeEventIds.add(eventId);
                ensureEventListeners(uid, reg);
            }
        }

        // Also watch events organized by this user if they are an Organizer
        if (activeProfile != null && activeProfile.getRoleEnum() == Role.ORGANIZER) {
            FirebaseFirestore.getInstance().collection("events")
                    .whereArrayContains("organizers", uid)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap == null || !uid.equals(activeUid)) {
                            return;
                        }
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            Event e = doc.toObject(Event.class);
                            if (e != null) {
                                e.setId(doc.getId());
                                ensureOrganizerEventWatch(uid, e);
                            }
                        }
                    });
        }
    }

    private void ensureEventListeners(@NonNull final String uid, @NonNull final MyRegistration reg) {
        final String eventId = reg.getEventId();
        if (eventId == null || eventId.isEmpty()) {
            return;
        }

        // A. Watch the Event document itself for updates (time, venue, status) or cancellation (deletion)
        if (!eventDocListeners.containsKey(eventId)) {
            ListenerRegistration docReg = EventRepository.getInstance(app).listenEvent(eventId,
                    new EventRepository.EventListener() {
                        @Override
                        public void onEvent(Event event) {
                            if (!uid.equals(activeUid)) {
                                return;
                            }
                            onWatchedEventChanged(uid, event, false);
                        }

                        @Override
                        public void onMissing() {
                            if (!uid.equals(activeUid)) {
                                return;
                            }
                            EventSnapshot old = eventMemory.remove(eventId);
                            String title = old != null ? old.title : reg.getTitle();
                            String cancelKey = "event_cancelled_" + uid + "_" + eventId;
                            if (!statePrefs.getBoolean(cancelKey, false)) {
                                statePrefs.edit().putBoolean(cancelKey, true).apply();
                                ToastCenter.eventUpdatedOrCancelled(app, title, null, true, eventId);
                            }
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });
            eventDocListeners.put(eventId, docReg);
        }

        // Only attach check-in, live poll, Q&A, and team listeners when REGISTERED
        if (!MyRegistration.REGISTERED.equals(reg.getStatus())) {
            return;
        }

        // B. Watch my check-in record for this event (unlocks certificate & feedback toasts)
        if (!eventCheckInMyListeners.containsKey(eventId)) {
            ListenerRegistration ciReg = CheckInRepository.listenMyCheckIn(eventId, uid,
                    new CheckInRepository.MyCheckInListener() {
                        @Override
                        public void onChanged(@Nullable CheckIn checkIn) {
                            if (!uid.equals(activeUid)) {
                                return;
                            }
                            boolean nowCheckedIn = (checkIn != null);
                            Boolean wasCheckedIn = checkInMemory.put(eventId, nowCheckedIn);
                            if (wasCheckedIn != null && !wasCheckedIn && nowCheckedIn) {
                                EventSnapshot ev = eventMemory.get(eventId);
                                String title = ev != null ? ev.title : reg.getTitle();
                                ToastCenter.show(app, "Checked in to " + safe(title, "event") + "!");
                            }
                            if (nowCheckedIn) {
                                checkTimeBasedTransitions();
                            }
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });
            eventCheckInMyListeners.put(eventId, ciReg);
        }

        // C. Watch live polls for this event (Scenario 10: Live poll started)
        if (!eventPollsListeners.containsKey(eventId)) {
            ListenerRegistration pollsReg = LiveRepository.listenPolls(eventId,
                    new LiveRepository.PollsListener() {
                        @Override
                        public void onPolls(List<Poll> polls) {
                            if (!uid.equals(activeUid)) {
                                return;
                            }
                            handleEventPollsSnapshot(uid, eventId, reg.getTitle(), polls);
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });
            eventPollsListeners.put(eventId, pollsReg);
        }

        // D. Watch live Q&A questions for this event (Scenario 10: Live Q&A started / new question)
        if (!eventQuestionsListeners.containsKey(eventId)) {
            ListenerRegistration qaReg = LiveRepository.listenQuestions(eventId,
                    new LiveRepository.QuestionsListener() {
                        @Override
                        public void onQuestions(List<Question> questions) {
                            if (!uid.equals(activeUid)) {
                                return;
                            }
                            handleEventQuestionsSnapshot(uid, eventId, reg.getTitle(), questions);
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });
            eventQuestionsListeners.put(eventId, qaReg);
        }

        // E. Watch teams & seekers for competition events (Scenario 8: Team invites & join requests)
        if (!eventTeamsListeners.containsKey(eventId)) {
            ListenerRegistration teamsReg = TeamRepository.listenTeams(eventId,
                    new TeamRepository.TeamsListener() {
                        @Override
                        public void onTeams(List<Team> teams) {
                            if (!uid.equals(activeUid)) {
                                return;
                            }
                            handleEventTeamsSnapshot(uid, eventId, teams);
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });
            eventTeamsListeners.put(eventId, teamsReg);
        }
    }

    private void ensureOrganizerEventWatch(@NonNull final String uid, @NonNull final Event e) {
        final String eventId = e.getId();
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        if (!eventDocListeners.containsKey(eventId)) {
            ListenerRegistration docReg = EventRepository.getInstance(app).listenEvent(eventId,
                    new EventRepository.EventListener() {
                        @Override
                        public void onEvent(Event event) {
                            if (!uid.equals(activeUid)) {
                                return;
                            }
                            onWatchedEventChanged(uid, event, true);
                        }

                        @Override
                        public void onMissing() {
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });
            eventDocListeners.put(eventId, docReg);
        }
    }

    private void onWatchedEventChanged(@NonNull String uid, @NonNull Event event, boolean asOrganizer) {
        String eventId = event.getId();
        if (eventId == null) {
            return;
        }
        EventSnapshot next = EventSnapshot.from(event, asOrganizer);
        EventSnapshot prev = eventMemory.put(eventId, next);

        String sigKey = "event_sig_" + uid + "_" + eventId;
        String prevSig = statePrefs.getString(sigKey, null);
        String nextSig = next.signature();

        if (prev != null) {
            List<String> changes = new ArrayList<>();
            if (!Objects.equals(prev.startMillis, next.startMillis)
                    || !Objects.equals(prev.endMillis, next.endMillis)) {
                changes.add("new time: " + TimeFormats.dateTime(event.getStartTime()));
            }
            if (!Objects.equals(prev.venue, next.venue)) {
                changes.add("new venue: " + safe(next.venue, "TBA"));
            }
            if (!Objects.equals(prev.title, next.title)) {
                changes.add("renamed to " + safe(next.title, "Event"));
            }
            if (!changes.isEmpty() && !uid.equals(event.getCreatedBy())
                    && !event.hasOrganizer(uid)) {
                String summary = android.text.TextUtils.join(", ", changes);
                ToastCenter.eventUpdatedOrCancelled(app, next.title, summary, false, eventId);
            }
        } else if (prevSig != null && !prevSig.equals(nextSig)
                && !uid.equals(event.getCreatedBy()) && !event.hasOrganizer(uid)) {
            ToastCenter.eventUpdatedOrCancelled(app, next.title,
                    "schedule or venue updated", false, eventId);
        }
        statePrefs.edit().putString(sigKey, nextSig).apply();

        checkTimeBasedTransitions();
    }

    private void handleEventPollsSnapshot(@NonNull String uid, @NonNull String eventId,
                                          @Nullable String fallbackTitle,
                                          @NonNull List<Poll> polls) {
        EventSnapshot ev = eventMemory.get(eventId);
        String eventTitle = ev != null ? ev.title : fallbackTitle;
        Set<String> known = knownPollsMemory.get(eventId);
        if (known == null) {
            known = new HashSet<>();
            for (Poll p : polls) {
                if (p.getId() != null) {
                    known.add(p.getId());
                }
            }
            knownPollsMemory.put(eventId, known);
            return;
        }
        for (Poll p : polls) {
            if (p.getId() != null && known.add(p.getId())) {
                if (!uid.equals(p.getCreatedBy()) && p.acceptingVotes()) {
                    ToastCenter.livePollOrQaStarted(app, eventTitle, p.getQuestion(),
                            true, eventId, p.getId());
                }
            }
        }
    }

    private void handleEventQuestionsSnapshot(@NonNull String uid, @NonNull String eventId,
                                              @Nullable String fallbackTitle,
                                              @NonNull List<Question> questions) {
        EventSnapshot ev = eventMemory.get(eventId);
        String eventTitle = ev != null ? ev.title : fallbackTitle;
        Set<String> known = knownQuestionsMemory.get(eventId);
        if (known == null) {
            known = new HashSet<>();
            for (Question q : questions) {
                if (q.getId() != null) {
                    known.add(q.getId());
                }
            }
            knownQuestionsMemory.put(eventId, known);
            return;
        }
        for (Question q : questions) {
            if (q.getId() != null && known.add(q.getId())) {
                if (!uid.equals(q.getAuthorUid())) {
                    ToastCenter.livePollOrQaStarted(app, eventTitle, q.getText(),
                            false, eventId, q.getId());
                }
            }
        }
    }

    // ------------------------------------------------------------------ 8: Team Invites & Join Requests

    private void handleTeamInvitesSnapshot(@NonNull String uid, @NonNull List<TeamInvite> invites) {
        if (!invitesInitialized) {
            invitesInitialized = true;
            for (TeamInvite i : invites) {
                if (i.getTeamId() != null) {
                    knownInvitesMemory.add(i.getTeamId());
                    String seenKey = "invite_seen_" + uid + "_" + i.getTeamId();
                    if (!statePrefs.getBoolean(seenKey, false)) {
                        statePrefs.edit().putBoolean(seenKey, true).apply();
                        ToastCenter.teamInviteReceived(app, i.getFromName(), i.getTeamName(),
                                i.getEventTitle(), i.getEventId(), i.getTeamId());
                    }
                }
            }
            return;
        }
        for (TeamInvite i : invites) {
            if (i.getTeamId() != null && knownInvitesMemory.add(i.getTeamId())) {
                String seenKey = "invite_seen_" + uid + "_" + i.getTeamId();
                statePrefs.edit().putBoolean(seenKey, true).apply();
                ToastCenter.teamInviteReceived(app, i.getFromName(), i.getTeamName(),
                        i.getEventTitle(), i.getEventId(), i.getTeamId());
            }
        }
    }

    private void handleEventTeamsSnapshot(@NonNull final String uid, @NonNull final String eventId,
                                          @NonNull List<Team> teams) {
        for (Team t : teams) {
            if (t == null || t.getId() == null) {
                continue;
            }
            if (t.hasMember(uid)) {
                String teamId = t.getId();
                Set<String> prevMembers = knownTeamMembersMemory.get(teamId);
                List<String> curMembers = t.getMemberUids() == null
                        ? new ArrayList<>() : t.getMemberUids();
                if (prevMembers == null) {
                    knownTeamMembersMemory.put(teamId, new HashSet<>(curMembers));
                } else {
                    for (String mUid : curMembers) {
                        if (prevMembers.add(mUid) && !uid.equals(mUid)) {
                            String mName = t.nameOf(mUid);
                            ToastCenter.teamMemberJoinedOrRequest(app,
                                    mName.isEmpty() ? "A new teammate" : mName,
                                    t.getName(), false, eventId);
                        }
                    }
                }

                // If user is leader of an non-full team, also watch seekers ("looking for team" requests)
                if (uid.equals(t.getLeaderUid()) && !t.full()
                        && !eventSeekersListeners.containsKey(eventId)) {
                    final String teamName = t.getName();
                    ListenerRegistration sReg = TeamRepository.listenSeekers(eventId,
                            new TeamRepository.SeekersListener() {
                                @Override
                                public void onSeekers(List<Seeker> seekers) {
                                    if (!uid.equals(activeUid)) {
                                        return;
                                    }
                                    Set<String> known = knownSeekersMemory.get(eventId);
                                    if (known == null) {
                                        known = new HashSet<>();
                                        for (Seeker s : seekers) {
                                            if (s.getUid() != null) {
                                                known.add(s.getUid());
                                            }
                                        }
                                        knownSeekersMemory.put(eventId, known);
                                        return;
                                    }
                                    for (Seeker s : seekers) {
                                        if (s.getUid() != null && known.add(s.getUid())
                                                && !uid.equals(s.getUid())) {
                                            ToastCenter.teamMemberJoinedOrRequest(app,
                                                    s.getName(), teamName, true, eventId);
                                        }
                                    }
                                }

                                @Override
                                public void onError(String message) {
                                }
                            });
                    eventSeekersListeners.put(eventId, sReg);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 3, 4, 6, 7: Clubs, Memberships, Notices, Group Chat

    private void handleClubsSnapshot(@NonNull final String uid, @NonNull List<Club> clubs) {
        for (Club club : clubs) {
            if (club == null || club.getId() == null) {
                continue;
            }
            final String clubId = club.getId();
            knownClubs.put(clubId, club);
            final boolean isOrganizerOfClub = club.hasOrganizer(uid)
                    || (activeProfile != null && activeProfile.getRoleEnum() == Role.ADMIN);

            if (isOrganizerOfClub) {
                memberOrOrganizerClubIds.add(clubId);
                ensureOrganizerClubMembersListener(uid, club);
                ensureClubNoticesAndChatListeners(uid, club);
            }

            // Watch my own membership document in every approved club
            if (club.isApproved() && !clubMyMembershipListeners.containsKey(clubId)) {
                ListenerRegistration myMemReg = MembershipRepository.listenMine(clubId, uid,
                        new MembershipRepository.MyMembershipListener() {
                            @Override
                            public void onChanged(@Nullable ClubMember member) {
                                if (!uid.equals(activeUid)) {
                                    return;
                                }
                                Club latestClub = knownClubs.get(clubId);
                                String clubName = latestClub != null ? latestClub.getName() : club.getName();
                                handleMyClubMembershipChange(uid, clubId, clubName, member);
                            }

                            @Override
                            public void onError(String message) {
                            }
                        });
                clubMyMembershipListeners.put(clubId, myMemReg);
            }
        }
    }

    /** Scenario 3: Club join approved or rejected (to the student). */
    private void handleMyClubMembershipChange(@NonNull String uid, @NonNull String clubId,
                                              @Nullable String clubName,
                                              @Nullable ClubMember member) {
        String newStatus = member == null || member.getStatus() == null ? "none" : member.getStatus();
        String prevStatus = clubMyStatusMemory.put(clubId, newStatus);
        String prefKey = "club_mem_status_" + uid + "_" + clubId;
        String savedStatus = statePrefs.getString(prefKey, null);

        boolean transitionFromPendingInMem = ClubMember.PENDING.equals(prevStatus)
                && (ClubMember.APPROVED.equals(newStatus) || ClubMember.REJECTED.equals(newStatus));
        boolean transitionFromPendingSaved = prevStatus == null
                && ClubMember.PENDING.equals(savedStatus)
                && (ClubMember.APPROVED.equals(newStatus) || ClubMember.REJECTED.equals(newStatus));

        if (transitionFromPendingInMem || transitionFromPendingSaved) {
            boolean approved = ClubMember.APPROVED.equals(newStatus);
            ToastCenter.clubJoinDecision(app, clubName, approved, clubId);
        }

        statePrefs.edit().putString(prefKey, newStatus).apply();

        if (ClubMember.APPROVED.equals(newStatus)) {
            memberOrOrganizerClubIds.add(clubId);
            Club c = knownClubs.get(clubId);
            if (c != null) {
                ensureClubNoticesAndChatListeners(uid, c);
            }
        }
    }

    /** Scenario 4: New join request (to club organizers). */
    private void ensureOrganizerClubMembersListener(@NonNull final String uid,
                                                    @NonNull final Club club) {
        final String clubId = club.getId();
        if (clubAllMembersListeners.containsKey(clubId)) {
            return;
        }
        ListenerRegistration reg = MembershipRepository.listenAll(clubId,
                new MembershipRepository.MembersListener() {
                    @Override
                    public void onMembers(List<ClubMember> members) {
                        if (!uid.equals(activeUid)) {
                            return;
                        }
                        Set<String> knownPending = clubPendingUidsMemory.get(clubId);
                        if (knownPending == null) {
                            knownPending = new HashSet<>();
                            for (ClubMember m : members) {
                                if (m.getUid() != null && ClubMember.PENDING.equals(m.getStatus())) {
                                    knownPending.add(m.getUid());
                                }
                            }
                            clubPendingUidsMemory.put(clubId, knownPending);
                            return;
                        }
                        Set<String> currentPending = new HashSet<>();
                        for (ClubMember m : members) {
                            if (m.getUid() != null && ClubMember.PENDING.equals(m.getStatus())) {
                                currentPending.add(m.getUid());
                                if (!knownPending.contains(m.getUid()) && !uid.equals(m.getUid())) {
                                    Club latest = knownClubs.get(clubId);
                                    String cName = latest != null ? latest.getName() : club.getName();
                                    ToastCenter.newClubJoinRequest(app, m.getName(), cName,
                                            clubId, m.getUid());
                                }
                            }
                        }
                        clubPendingUidsMemory.put(clubId, currentPending);
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
        clubAllMembersListeners.put(clubId, reg);
    }

    /** Scenarios 6 & 7: New club notice/pinned notice + Group chat messages/mentions (with mute switch). */
    private void ensureClubNoticesAndChatListeners(@NonNull final String uid,
                                                   @NonNull final Club club) {
        final String clubId = club.getId();

        // Scenario 6: Club notices & pinned notices
        if (!clubNoticesListeners.containsKey(clubId)) {
            ListenerRegistration nReg = ClubNoticeRepository.listen(app, clubId, club, notices -> {
                if (!uid.equals(activeUid)) {
                    return;
                }
                Set<String> known = clubNoticeIdsMemory.get(clubId);
                if (known == null) {
                    known = new HashSet<>();
                    for (ClubNotice n : notices) {
                        if (n.getId() != null) {
                            known.add(n.getId());
                        }
                    }
                    clubNoticeIdsMemory.put(clubId, known);
                    return;
                }
                for (ClubNotice n : notices) {
                    String nId = n.getId();
                    if (nId == null || nId.startsWith("default_")) {
                        continue;
                    }
                    if (known.add(nId) && !uid.equals(n.getAuthorUid())) {
                        Club latest = knownClubs.get(clubId);
                        String cName = latest != null ? latest.getName() : club.getName();
                        ToastCenter.newClubNotice(app, cName, n.getTitle(), n.isPinned(), nId);
                    }
                }
            });
            clubNoticesListeners.put(clubId, nReg);
        }

        // Scenario 7: Club group chat messages & @mentions (respecting per-club mute switch)
        if (!clubChatHandles.containsKey(clubId)) {
            ClubChatRepository.Handle handle = ClubChatRepository.listen(app, clubId,
                    new ClubChatRepository.MessagesListener() {
                        @Override
                        public void onMessages(List<ChatMessage> messages) {
                            if (!uid.equals(activeUid)) {
                                return;
                            }
                            Set<String> known = clubChatIdsMemory.get(clubId);
                            if (known == null) {
                                known = new HashSet<>();
                                for (ChatMessage m : messages) {
                                    if (m.getId() != null) {
                                        known.add(m.getId());
                                    }
                                }
                                clubChatIdsMemory.put(clubId, known);
                                return;
                            }
                            for (ChatMessage m : messages) {
                                if (m.getId() == null || !known.add(m.getId())) {
                                    continue;
                                }
                                // Skip own messages
                                if (uid.equals(m.getSenderUid())) {
                                    continue;
                                }
                                // Respect per-club mute switch!
                                if (NotificationPrefs.isClubChatMuted(app, clubId)) {
                                    continue;
                                }
                                // If user is actively looking at this exact club's chat screen, skip toast unless mentioned
                                boolean mentioned = isUserMentioned(activeProfile, m.getText());
                                if (clubId.equals(activeOpenChatClubId) && !mentioned) {
                                    continue;
                                }
                                Club latest = knownClubs.get(clubId);
                                String cName = latest != null ? latest.getName() : club.getName();
                                ToastCenter.clubChatMessage(app, clubId, cName,
                                        m.getSenderName(), m.getText(), mentioned, m.getId());
                            }
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });
            clubChatHandles.put(clubId, handle);
        }
    }

    private static boolean isUserMentioned(@Nullable UserProfile profile, @Nullable String text) {
        if (profile == null || text == null || text.isEmpty()) {
            return false;
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        if (lowerText.contains("@all") || lowerText.contains("@everyone")) {
            return true;
        }
        String fullName = profile.getName() == null ? "" : profile.getName().trim().toLowerCase(Locale.ROOT);
        if (!fullName.isEmpty()) {
            if (lowerText.contains("@" + fullName)) {
                return true;
            }
            String firstName = fullName.split("\\s+")[0];
            if (firstName.length() >= 2 && lowerText.contains("@" + firstName)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 9, 11, 12: Time-based Transitions

    /**
     * Periodically checks watched events for:
     * - Scenario 12: Event starting / check-in open (within 15m before start or currently live)
     * - Scenario 9: Certificate ready (“Your certificate for <event> is available.”) after event ends for checked-in students
     * - Scenario 11: Feedback prompt (“How was <event>?”) after event ends for checked-in students
     */
    public void checkTimeBasedTransitions() {
        final String uid = activeUid;
        if (uid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, EventSnapshot> entry : eventMemory.entrySet()) {
            String eventId = entry.getKey();
            EventSnapshot ev = entry.getValue();
            if (ev == null || ev.startMillis <= 0) {
                continue;
            }
            boolean isReg = MyRegistration.REGISTERED.equals(regStatusMemory.get(eventId));
            boolean isOrg = ev.organizer;
            if (!isReg && !isOrg) {
                continue;
            }

            long effectiveEnd = ev.endMillis > 0 ? ev.endMillis : ev.startMillis + 3600_000L;

            // Scenario 12: Event starting soon or live now -> check-in open
            boolean startingOrLive = now >= (ev.startMillis - EVENT_STARTING_SOON_WINDOW_MS)
                    && now <= effectiveEnd;
            if (startingOrLive) {
                String startToastKey = "toast_event_starting_" + uid + "_" + eventId;
                if (!statePrefs.getBoolean(startToastKey, false)) {
                    statePrefs.edit().putBoolean(startToastKey, true).apply();
                    ToastCenter.eventStartingOrCheckInOpen(app, ev.title, false, isOrg, eventId);
                }
            }

            // Scenarios 9 & 11: Event has ended and student was checked in
            boolean ended = now > effectiveEnd;
            boolean checkedIn = Boolean.TRUE.equals(checkInMemory.get(eventId));
            if (ended && checkedIn) {
                // Scenario 9: Certificate ready
                String certKey = "toast_cert_ready_" + uid + "_" + eventId;
                if (!statePrefs.getBoolean(certKey, false)) {
                    statePrefs.edit().putBoolean(certKey, true).apply();
                    ToastCenter.certificateReady(app, ev.title, eventId);
                }

                // Scenario 11: Feedback prompt (“How was <event>?”)
                String fbKey = "toast_feedback_prompt_" + uid + "_" + eventId;
                if (!statePrefs.getBoolean(fbKey, false)) {
                    statePrefs.edit().putBoolean(fbKey, true).apply();
                    handler.postDelayed(() ->
                            ToastCenter.feedbackPrompt(app, ev.title, eventId), 3_500L);
                }
            }
        }
    }

    private static String safe(@Nullable String s, @NonNull String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }

    private static final class EventSnapshot {
        final String id;
        final String title;
        final String venue;
        final long startMillis;
        final long endMillis;
        final boolean organizer;

        EventSnapshot(String id, String title, String venue, long startMillis, long endMillis,
                      boolean organizer) {
            this.id = id;
            this.title = title;
            this.venue = venue;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.organizer = organizer;
        }

        static EventSnapshot from(Event e, boolean organizer) {
            Date start = e.getStartTime();
            Date end = e.getEndTime();
            return new EventSnapshot(
                    e.getId() == null ? "" : e.getId(),
                    e.getTitle() == null ? "" : e.getTitle(),
                    e.getVenue() == null ? "" : e.getVenue(),
                    start == null ? 0L : start.getTime(),
                    end == null ? 0L : end.getTime(),
                    organizer || e.typeEnum() == EventType.COMPETITION
            );
        }

        String signature() {
            return (title == null ? "" : title) + "|"
                    + (venue == null ? "" : venue) + "|"
                    + startMillis + "|" + endMillis;
        }
    }
}

package com.kuet.clubhub.data;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Seeker;
import com.kuet.clubhub.model.Team;
import com.kuet.clubhub.model.TeamInvite;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.Codes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Teams for competitions.
 *
 *   teams/{teamId}                          the team (members, skills wanted, open or invite-only)
 *   teams/{teamId}/private/invite           the invite code, readable by members only
 *   inviteCodes/{code}                      code -> teamId lookup (get only, no listing)
 *   events/{eventId}/teamMembers/{uid}      "this student is in team X": created once, so a
 *                                           student can never be in two teams of the same event
 *   events/{eventId}/seekers/{uid}          the looking-for-teammates board
 *   users/{uid}/teamInvites/{teamId}        invitations from team leaders
 *
 * Joining runs in a transaction that checks the team size, so the last place cannot be taken
 * twice; the Firestore rules repeat every one of these checks on the server.
 */
public final class TeamRepository {

    private static final String TEAMS = "teams";
    private static final String EVENTS = "events";
    private static final String USERS = "users";
    private static final String TEAM_MEMBERS = "teamMembers";
    private static final String SEEKERS = "seekers";
    private static final String INVITE_CODES = "inviteCodes";
    private static final String PRIVATE = "private";
    private static final String INVITE_DOC = "invite";
    private static final String INVITES = "teamInvites";
    private static final String REGISTRATIONS = "registrations";

    public static final String VIA_LEADER = "leader";
    public static final String VIA_OPEN = "open";
    public static final String VIA_CODE = "code";
    public static final String VIA_INVITE = "invite";

    public interface TeamsListener {
        void onTeams(List<Team> teams);

        void onError(String message);
    }

    public interface TeamListener {
        void onTeam(Team team);

        void onMissing();

        void onError(String message);
    }

    public interface SeekersListener {
        void onSeekers(List<Seeker> seekers);

        void onError(String message);
    }

    public interface InvitesListener {
        void onInvites(List<TeamInvite> invites);

        void onError(String message);
    }

    /** teamId is null when the student is not in a team for the event. */
    public interface MyTeamListener {
        void onChanged(@Nullable String teamId);

        void onError(String message);
    }

    private TeamRepository() {
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static DocumentReference teamRef(String teamId) {
        return db().collection(TEAMS).document(teamId);
    }

    private static DocumentReference claimRef(String eventId, String uid) {
        return db().collection(EVENTS).document(eventId).collection(TEAM_MEMBERS).document(uid);
    }

    private static DocumentReference seekerRef(String eventId, String uid) {
        return db().collection(EVENTS).document(eventId).collection(SEEKERS).document(uid);
    }

    private static DocumentReference inviteRef(String uid, String teamId) {
        return db().collection(USERS).document(uid).collection(INVITES).document(teamId);
    }

    // ---------------------------------------------------------------- live data

    public static ListenerRegistration listenTeams(String eventId, TeamsListener listener) {
        return db().collection(TEAMS).whereEqualTo("eventId", eventId)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    List<Team> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Team t = toTeam(d);
                        if (t != null) {
                            list.add(t);
                        }
                    }
                    listener.onTeams(list);
                });
    }

    public static ListenerRegistration listenTeam(String teamId, TeamListener listener) {
        return teamRef(teamId).addSnapshotListener((doc, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            Team t = doc == null || !doc.exists() ? null : toTeam(doc);
            if (t == null) {
                listener.onMissing();
            } else {
                listener.onTeam(t);
            }
        });
    }

    public static ListenerRegistration listenMyTeam(String eventId, String uid,
                                                    MyTeamListener listener) {
        return claimRef(eventId, uid).addSnapshotListener((doc, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            listener.onChanged(doc == null || !doc.exists() ? null : doc.getString("teamId"));
        });
    }

    public static ListenerRegistration listenSeekers(String eventId, SeekersListener listener) {
        return db().collection(EVENTS).document(eventId).collection(SEEKERS)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    List<Seeker> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Seeker s = d.toObject(Seeker.class);
                        if (s != null) {
                            s.setUid(d.getId());
                            list.add(s);
                        }
                    }
                    listener.onSeekers(list);
                });
    }

    public static ListenerRegistration listenInvites(String uid, InvitesListener listener) {
        return db().collection(USERS).document(uid).collection(INVITES)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    List<TeamInvite> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        TeamInvite i = d.toObject(TeamInvite.class);
                        if (i != null) {
                            i.setTeamId(d.getId());
                            list.add(i);
                        }
                    }
                    listener.onInvites(list);
                });
    }

    /** Members only (the rules refuse everyone else). */
    public static void loadInviteCode(String teamId, Callback<String> cb) {
        teamRef(teamId).collection(PRIVATE).document(INVITE_DOC).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        cb.onError(describe(task.getException()));
                        return;
                    }
                    String code = task.getResult().getString("code");
                    if (code == null) {
                        cb.onError("This team has no invite code.");
                    } else {
                        cb.onSuccess(code);
                    }
                });
    }

    // ---------------------------------------------------------------- create / edit / disband

    /** Creates a team led by the student. The callback receives the new team id. */
    public static void createTeam(Event event, UserProfile user, String name,
                                  List<String> skillsNeeded, String note, boolean open,
                                  Callback<String> cb) {
        final String uid = user.getUid();
        final String code = Codes.random(8);
        final DocumentReference teamRef = db().collection(TEAMS).document();

        List<String> mySkills = user.getSkills() == null
                ? new ArrayList<String>() : new ArrayList<>(user.getSkills());
        List<String> members = new ArrayList<>();
        members.add(uid);
        Map<String, Object> names = new HashMap<>();
        names.put(uid, user.getName());
        Map<String, Object> skills = new HashMap<>();
        skills.put(uid, mySkills);

        Map<String, Object> team = new HashMap<>();
        team.put("eventId", event.getId());
        team.put("eventTitle", event.getTitle());
        team.put("name", name);
        team.put("leaderUid", uid);
        team.put("leaderName", user.getName());
        team.put("note", note);
        team.put("open", open);
        team.put("minSize", event.getTeamSizeMin());
        team.put("maxSize", event.getTeamSizeMax());
        team.put("memberUids", members);
        team.put("memberNames", names);
        team.put("memberSkills", skills);
        team.put("skillsNeeded", skillsNeeded);
        team.put("createdAt", FieldValue.serverTimestamp());
        team.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> secret = new HashMap<>();
        secret.put("code", code);

        Map<String, Object> lookup = new HashMap<>();
        lookup.put("teamId", teamRef.getId());
        lookup.put("eventId", event.getId());

        Map<String, Object> claim = new HashMap<>();
        claim.put("uid", uid);
        claim.put("teamId", teamRef.getId());
        claim.put("via", VIA_LEADER);
        claim.put("name", user.getName());
        claim.put("joinedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db().batch();
        batch.set(teamRef, team);
        batch.set(teamRef.collection(PRIVATE).document(INVITE_DOC), secret);
        batch.set(db().collection(INVITE_CODES).document(code), lookup);
        batch.set(claimRef(event.getId(), uid), claim);
        // A student who had posted "I need a team" no longer needs the post.
        batch.delete(seekerRef(event.getId(), uid));
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(teamRef.getId());
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    /** The leader changes the name, wanted skills, note or open / invite-only setting. */
    public static void updateTeam(String teamId, String name, List<String> skillsNeeded,
                                  String note, boolean open, Callback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("skillsNeeded", skillsNeeded);
        data.put("note", note);
        data.put("open", open);
        data.put("updatedAt", FieldValue.serverTimestamp());
        teamRef(teamId).update(data).addOnCompleteListener(task -> done(task, cb));
    }

    /** The leader deletes the team and frees every member to join another one. */
    public static void disband(Team team, Callback<Void> cb) {
        final DocumentReference teamRef = teamRef(team.getId());
        final DocumentReference codeDoc = teamRef.collection(PRIVATE).document(INVITE_DOC);
        codeDoc.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                cb.onError(describe(task.getException()));
                return;
            }
            DocumentSnapshot secret = task.getResult();
            String code = secret == null ? null : secret.getString("code");

            WriteBatch batch = db().batch();
            for (String memberUid : team.getMemberUids()) {
                batch.delete(claimRef(team.getEventId(), memberUid));
            }
            if (code != null) {
                batch.delete(db().collection(INVITE_CODES).document(code));
            }
            batch.delete(codeDoc);
            batch.delete(teamRef);
            batch.commit().addOnCompleteListener(commit -> done(commit, cb));
        });
    }

    // ---------------------------------------------------------------- joining and leaving

    /** Joins an open team directly from the board. */
    public static void joinOpenTeam(String teamId, UserProfile user, Callback<Void> cb) {
        joinTeam(teamId, user, VIA_OPEN, null, cb);
    }

    /** Joins with the code a teammate shared (typed in, or read from the team's QR code). */
    public static void joinWithCode(String code, UserProfile user, Callback<Void> cb) {
        final String clean = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (clean.isEmpty() || clean.contains("/")) {
            cb.onError("Enter the invite code.");
            return;
        }
        db().collection(INVITE_CODES).document(clean).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                cb.onError(describe(task.getException()));
                return;
            }
            DocumentSnapshot lookup = task.getResult();
            String teamId = lookup.exists() ? lookup.getString("teamId") : null;
            if (teamId == null) {
                cb.onError("No team uses that code. Check it and try again.");
                return;
            }
            joinTeam(teamId, user, VIA_CODE, clean, cb);
        });
    }

    /** Accepts a leader's invitation. */
    public static void acceptInvite(TeamInvite invite, UserProfile user, Callback<Void> cb) {
        joinTeam(invite.getTeamId(), user, VIA_INVITE, null, cb);
    }

    public static void declineInvite(String uid, String teamId, Callback<Void> cb) {
        inviteRef(uid, teamId).delete().addOnCompleteListener(task -> done(task, cb));
    }

    private static void joinTeam(String teamId, UserProfile user, String via,
                                 @Nullable String code, Callback<Void> cb) {
        final String uid = user.getUid();
        final DocumentReference teamRef = teamRef(teamId);

        db().runTransaction((Transaction.Function<Void>) tx -> {
            DocumentSnapshot team = tx.get(teamRef);
            if (!team.exists()) {
                throw new FirebaseFirestoreException("This team no longer exists.",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            String eventId = team.getString("eventId");
            DocumentReference claim = claimRef(eventId, uid);
            DocumentSnapshot claimSnap = tx.get(claim);
            DocumentSnapshot reg = tx.get(db().collection(EVENTS).document(eventId)
                    .collection(REGISTRATIONS).document(uid));

            if (claimSnap.exists()) {
                throw new FirebaseFirestoreException("You are already in a team for this event.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            if (!reg.exists() || !MyRegistration.REGISTERED.equals(reg.getString("status"))) {
                throw new FirebaseFirestoreException(
                        "Register for the event (not just the waiting list) before joining a team.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            @SuppressWarnings("unchecked")
            List<String> members = (List<String>) team.get("memberUids");
            Long max = team.getLong("maxSize");
            if (members != null && max != null && members.size() >= max) {
                throw new FirebaseFirestoreException("This team is already full.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            if (VIA_OPEN.equals(via) && !Boolean.TRUE.equals(team.getBoolean("open"))) {
                throw new FirebaseFirestoreException("This team is invite-only.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            List<String> skills = user.getSkills() == null
                    ? new ArrayList<String>() : new ArrayList<>(user.getSkills());
            tx.update(teamRef,
                    "memberUids", FieldValue.arrayUnion(uid),
                    "memberNames." + uid, user.getName(),
                    "memberSkills." + uid, skills,
                    "updatedAt", FieldValue.serverTimestamp());

            Map<String, Object> claimData = new HashMap<>();
            claimData.put("uid", uid);
            claimData.put("teamId", teamId);
            claimData.put("via", via);
            claimData.put("name", user.getName());
            claimData.put("joinedAt", FieldValue.serverTimestamp());
            if (code != null) {
                claimData.put("code", code);
            }
            tx.set(claim, claimData);

            // Whatever got the student here, the invitation and the board post are used up.
            tx.delete(inviteRef(uid, teamId));
            tx.delete(seekerRef(eventId, uid));
            return null;
        }).addOnCompleteListener(task -> done(task, cb));
    }

    /** A member (not the leader) leaves. */
    public static void leave(Team team, String uid, Callback<Void> cb) {
        removeFromTeam(team, uid, cb);
    }

    /** The leader removes a member. */
    public static void removeMember(Team team, String memberUid, Callback<Void> cb) {
        removeFromTeam(team, memberUid, cb);
    }

    private static void removeFromTeam(Team team, String memberUid, Callback<Void> cb) {
        WriteBatch batch = db().batch();
        batch.update(teamRef(team.getId()),
                "memberUids", FieldValue.arrayRemove(memberUid),
                "memberNames." + memberUid, FieldValue.delete(),
                "memberSkills." + memberUid, FieldValue.delete(),
                "updatedAt", FieldValue.serverTimestamp());
        batch.delete(claimRef(team.getEventId(), memberUid));
        batch.commit().addOnCompleteListener(task -> done(task, cb));
    }

    // ---------------------------------------------------------------- looking-for-teammates board

    public static void postSeeker(String eventId, UserProfile user, String note, Callback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("name", user.getName());
        data.put("department", user.getDepartment());
        data.put("year", user.getYear());
        data.put("skills", user.getSkills() == null
                ? new ArrayList<String>() : new ArrayList<>(user.getSkills()));
        data.put("note", note == null ? "" : note);
        data.put("updatedAt", FieldValue.serverTimestamp());
        seekerRef(eventId, user.getUid()).set(data).addOnCompleteListener(task -> done(task, cb));
    }

    public static void removeSeeker(String eventId, String uid, Callback<Void> cb) {
        seekerRef(eventId, uid).delete().addOnCompleteListener(task -> done(task, cb));
    }

    /** The leader invites someone from the board. */
    public static void invite(Team team, String targetUid, Callback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("teamId", team.getId());
        data.put("eventId", team.getEventId());
        data.put("eventTitle", team.getEventTitle());
        data.put("teamName", team.getName());
        data.put("fromUid", team.getLeaderUid());
        data.put("fromName", team.getLeaderName());
        data.put("createdAt", FieldValue.serverTimestamp());
        inviteRef(targetUid, team.getId()).set(data).addOnCompleteListener(task -> done(task, cb));
    }

    // ---------------------------------------------------------------- helpers

    @Nullable
    private static Team toTeam(DocumentSnapshot doc) {
        Team t = doc.toObject(Team.class);
        if (t != null) {
            t.setId(doc.getId());
        }
        return t;
    }

    private static void done(com.google.android.gms.tasks.Task<?> task, Callback<Void> cb) {
        if (task.isSuccessful()) {
            cb.onSuccess(null);
        } else {
            cb.onError(describe(task.getException()));
        }
    }

    private static String describe(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    || code == FirebaseFirestoreException.Code.NOT_FOUND) {
                return e.getMessage();
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Not allowed. You must be registered for this competition before it starts, "
                        + "not already in a team, and the team must have room. "
                        + "Also check that the latest firestore.rules is published.";
            }
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "You need an internet connection for this.";
            }
        }
        if (e != null && e.getMessage() != null) {
            return e.getMessage();
        }
        return "Something went wrong. Please try again.";
    }
}

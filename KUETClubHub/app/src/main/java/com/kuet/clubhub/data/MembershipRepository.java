package com.kuet.clubhub.data;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.kuet.clubhub.model.ClubMember;
import com.kuet.clubhub.model.UserProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Club membership applications at clubs/{clubId}/members/{uid}.
 * A student applies (status pending); an organizer of the club approves or rejects.
 * The club's memberCount moves together with the member document in one batch.
 */
public final class MembershipRepository {

    private static final String CLUBS = "clubs";
    private static final String MEMBERS = "members";

    /** member is null when the person never applied. */
    public interface MyMembershipListener {
        void onChanged(@Nullable ClubMember member);

        void onError(String message);
    }

    public interface MembersListener {
        void onMembers(List<ClubMember> members);

        void onError(String message);
    }

    private MembershipRepository() {
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static DocumentReference clubRef(String clubId) {
        return db().collection(CLUBS).document(clubId);
    }

    private static DocumentReference memberRef(String clubId, String uid) {
        return clubRef(clubId).collection(MEMBERS).document(uid);
    }

    // ---------------------------------------------------------------- live data

    public static ListenerRegistration listenMine(String clubId, String uid,
                                                  MyMembershipListener listener) {
        return memberRef(clubId, uid).addSnapshotListener((doc, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            if (doc == null || !doc.exists()) {
                listener.onChanged(null);
                return;
            }
            ClubMember m = doc.toObject(ClubMember.class);
            if (m != null) {
                m.setUid(doc.getId());
            }
            listener.onChanged(m);
        });
    }

    /** For organizers: every application and member of the club. */
    public static ListenerRegistration listenAll(String clubId, MembersListener listener) {
        return clubRef(clubId).collection(MEMBERS).addSnapshotListener((snap, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            if (snap == null) {
                return;
            }
            List<ClubMember> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                ClubMember m = d.toObject(ClubMember.class);
                if (m != null) {
                    m.setUid(d.getId());
                    list.add(m);
                }
            }
            listener.onMembers(list);
        });
    }

    /** Public directory of approved members for a club. */
    public static ListenerRegistration listenApproved(String clubId, MembersListener listener) {
        return clubRef(clubId).collection(MEMBERS)
                .whereEqualTo("status", ClubMember.APPROVED)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    List<ClubMember> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ClubMember m = d.toObject(ClubMember.class);
                        if (m != null) {
                            m.setUid(d.getId());
                            list.add(m);
                        }
                    }
                    listener.onMembers(list);
                });
    }

    // ---------------------------------------------------------------- student actions

    /** Sends an application, or applies again after a rejection. */
    public static void apply(String clubId, UserProfile user, String message, Callback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("name", user.getName());
        data.put("studentId", user.getStudentId());
        data.put("department", user.getDepartment());
        data.put("year", user.getYear());
        data.put("message", message == null ? "" : message);
        data.put("status", ClubMember.PENDING);
        data.put("appliedAt", FieldValue.serverTimestamp());
        memberRef(clubId, user.getUid()).set(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(null);
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    /** Withdraws an application, or leaves the club (which also lowers the member count). */
    public static void leave(String clubId, String uid, boolean wasApproved, Callback<Void> cb) {
        delete(clubId, uid, wasApproved, cb);
    }

    // ---------------------------------------------------------------- organizer actions

    public static void decide(String clubId, String uid, boolean approve, String deciderUid,
                              Callback<Void> cb) {
        WriteBatch batch = db().batch();
        Map<String, Object> update = new HashMap<>();
        update.put("status", approve ? ClubMember.APPROVED : ClubMember.REJECTED);
        update.put("decidedAt", FieldValue.serverTimestamp());
        update.put("decidedBy", deciderUid);
        batch.update(memberRef(clubId, uid), update);
        if (approve) {
            batch.update(clubRef(clubId), "memberCount", FieldValue.increment(1));
        }
        commit(batch, cb);
    }

    /** Removes a member (or a pending application) from the club. */
    public static void remove(String clubId, String uid, boolean wasApproved, Callback<Void> cb) {
        delete(clubId, uid, wasApproved, cb);
    }

    // ---------------------------------------------------------------- helpers

    private static void delete(String clubId, String uid, boolean wasApproved, Callback<Void> cb) {
        WriteBatch batch = db().batch();
        batch.delete(memberRef(clubId, uid));
        if (wasApproved) {
            batch.update(clubRef(clubId), "memberCount", FieldValue.increment(-1));
        }
        commit(batch, cb);
    }

    private static void commit(WriteBatch batch, Callback<Void> cb) {
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(null);
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    private static String describe(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Not allowed. The club must be approved, and the latest firestore.rules must be published.";
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

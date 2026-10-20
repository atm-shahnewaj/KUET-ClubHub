package com.kuet.clubhub.data;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.model.FeedbackEntry;
import com.kuet.clubhub.model.UserProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event feedback at events/{eventId}/feedback/{uid}. One entry per student; the Firestore rules
 * only accept it from a student whose check-in record exists, so attendance is verified.
 */
public final class FeedbackRepository {

    private static final String EVENTS = "events";
    private static final String FEEDBACK = "feedback";

    /** entry is null when the student has not rated the event. */
    public interface MyFeedbackListener {
        void onChanged(@Nullable FeedbackEntry entry);

        void onError(String message);
    }

    public interface FeedbackListener {
        void onFeedback(List<FeedbackEntry> entries);

        void onError(String message);
    }

    private FeedbackRepository() {
    }

    private static DocumentReference ref(String eventId, String uid) {
        return FirebaseFirestore.getInstance().collection(EVENTS).document(eventId)
                .collection(FEEDBACK).document(uid);
    }

    public static ListenerRegistration listenMine(String eventId, String uid,
                                                  MyFeedbackListener listener) {
        return ref(eventId, uid).addSnapshotListener((doc, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            if (doc == null || !doc.exists()) {
                listener.onChanged(null);
                return;
            }
            FeedbackEntry e = doc.toObject(FeedbackEntry.class);
            if (e != null) {
                e.setUid(doc.getId());
            }
            listener.onChanged(e);
        });
    }

    /** For organizers: all feedback for the event. */
    public static ListenerRegistration listenAll(String eventId, FeedbackListener listener) {
        return FirebaseFirestore.getInstance().collection(EVENTS).document(eventId)
                .collection(FEEDBACK).addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    List<FeedbackEntry> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        FeedbackEntry e = d.toObject(FeedbackEntry.class);
                        if (e != null) {
                            e.setUid(d.getId());
                            list.add(e);
                        }
                    }
                    listener.onFeedback(list);
                });
    }

    /** Saves (or replaces) the student's rating. rating is 1 to 5. */
    public static void save(String eventId, UserProfile user, int rating, String comment,
                            Callback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("name", user.getName());
        data.put("rating", rating);
        data.put("comment", comment == null ? "" : comment);
        data.put("updatedAt", FieldValue.serverTimestamp());
        ref(eventId, user.getUid()).set(data).addOnCompleteListener(task -> {
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
                return "Feedback is only accepted from students whose attendance was verified.";
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

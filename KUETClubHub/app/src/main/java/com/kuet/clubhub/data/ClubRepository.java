package com.kuet.clubhub.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.data.local.ClubDao;
import com.kuet.clubhub.data.local.ClubEntity;
import com.kuet.clubhub.model.Club;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Club data: approved clubs are mirrored from Firestore into Room (so the directory
 * works offline); other lists and single clubs are read straight from Firestore.
 */
public class ClubRepository {

    private static final String CLUBS = "clubs";

    /** Delivers a full list of clubs. */
    public interface ClubsListener {
        void onClubs(List<Club> clubs);

        void onError(String message);
    }

    /** Delivers one club. */
    public interface ClubListener {
        void onClub(Club club);

        void onMissing();

        void onError(String message);
    }

    /** Reports the state of the Firestore -> Room sync. */
    public interface SyncListener {
        /** A server snapshot was written to Room. */
        void onSynced();

        /** Only an empty local cache answered (offline, nothing saved yet). */
        void onOfflineEmpty();

        void onError(String message);
    }

    private static ClubRepository instance;

    private final ClubDao dao;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Executor io = Executors.newSingleThreadExecutor();

    private ClubRepository(Context context) {
        dao = AppDatabase.getInstance(context).clubDao();
    }

    public static synchronized ClubRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ClubRepository(context.getApplicationContext());
        }
        return instance;
    }

    // ---------------------------------------------------------------- directory (Room)

    public LiveData<List<ClubEntity>> observeCachedClubs() {
        return dao.observeAll();
    }

    /** Keeps Room in step with the approved clubs in Firestore. Remove the registration to stop. */
    public ListenerRegistration startSync(SyncListener listener) {
        return db.collection(CLUBS).whereEqualTo("approved", true)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    // An empty answer from the cache must not wipe clubs we already saved.
                    if (snap.getMetadata().isFromCache() && snap.isEmpty()) {
                        listener.onOfflineEmpty();
                        return;
                    }
                    List<ClubEntity> entities = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Club c = toClub(d);
                        if (c != null) {
                            entities.add(ClubEntity.fromClub(c));
                        }
                    }
                    io.execute(() -> dao.replaceAll(entities));
                    listener.onSynced();
                });
    }

    // ---------------------------------------------------------------- live Firestore lists

    public ListenerRegistration listenMyClubs(String uid, ClubsListener listener) {
        return listen(db.collection(CLUBS).whereArrayContains("organizers", uid), listener);
    }

    public ListenerRegistration listenPendingClubs(ClubsListener listener) {
        return listen(db.collection(CLUBS).whereEqualTo("approved", false), listener);
    }

    private ListenerRegistration listen(Query query, ClubsListener listener) {
        return query.addSnapshotListener((snap, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            if (snap == null) {
                return;
            }
            List<Club> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Club c = toClub(d);
                if (c != null) {
                    list.add(c);
                }
            }
            // Sorted here so no composite Firestore index is needed.
            Collections.sort(list, (a, b) -> safe(a.getName()).compareToIgnoreCase(safe(b.getName())));
            listener.onClubs(list);
        });
    }

    public ListenerRegistration listenClub(String clubId, ClubListener listener) {
        return db.collection(CLUBS).document(clubId).addSnapshotListener((doc, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            if (doc == null || !doc.exists()) {
                listener.onMissing();
                return;
            }
            Club c = toClub(doc);
            if (c == null) {
                listener.onMissing();
            } else {
                listener.onClub(c);
            }
        });
    }

    /** Approved clubs the person may create events for (admins: all approved clubs). */
    public void loadClubsForEvents(boolean isAdmin, String uid, Callback<List<Club>> cb) {
        Query query = isAdmin
                ? db.collection(CLUBS).whereEqualTo("approved", true)
                : db.collection(CLUBS).whereArrayContains("organizers", uid);
        query.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                cb.onError(describe(task.getException()));
                return;
            }
            List<Club> list = new ArrayList<>();
            for (DocumentSnapshot d : task.getResult().getDocuments()) {
                Club c = toClub(d);
                if (c != null && c.isApproved()) {
                    list.add(c);
                }
            }
            Collections.sort(list, (a, b) -> safe(a.getName()).compareToIgnoreCase(safe(b.getName())));
            cb.onSuccess(list);
        });
    }

    // ---------------------------------------------------------------- writes

    /** Admins create approved clubs; organizers create clubs that wait for approval. */
    public void createClub(String name, String category, String description,
                           String creatorUid, boolean createdByAdmin, Callback<String> cb) {
        List<String> organizers = new ArrayList<>();
        if (!createdByAdmin) {
            organizers.add(creatorUid);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("category", category);
        data.put("description", description);
        data.put("organizers", organizers);
        data.put("memberCount", 0L);
        data.put("approved", createdByAdmin);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection(CLUBS).add(data).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                cb.onSuccess(task.getResult().getId());
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    public void updateClub(String clubId, String name, String category, String description,
                           Callback<Void> cb) {
        db.collection(CLUBS).document(clubId)
                .update("name", name, "category", category, "description", description)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        cb.onSuccess(null);
                    } else {
                        cb.onError(describe(task.getException()));
                    }
                });
    }

    public void approveClub(String clubId, Callback<Void> cb) {
        db.collection(CLUBS).document(clubId).update("approved", true)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        cb.onSuccess(null);
                    } else {
                        cb.onError(describe(task.getException()));
                    }
                });
    }

    // ---------------------------------------------------------------- helpers

    private static Club toClub(DocumentSnapshot doc) {
        Club club = doc.toObject(Club.class);
        if (club != null) {
            club.setId(doc.getId());
        }
        return club;
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String describe(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "You don't have permission to do that. Check that firestore.rules is published.";
            }
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "You appear to be offline.";
            }
        }
        if (e != null && e.getMessage() != null) {
            return e.getMessage();
        }
        return "Something went wrong. Please try again.";
    }
}

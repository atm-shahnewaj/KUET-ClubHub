package com.kuet.clubhub.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.data.local.EventDao;
import com.kuet.clubhub.data.local.EventEntity;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Participant;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.Codes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Events, registrations, seat capacity and the waiting list.
 *
 * Seat counting and waiting-list moves happen inside Firestore transactions, so two students
 * can never take the same last seat.
 */
public class EventRepository {

    private static final String EVENTS = "events";
    private static final String REGISTRATIONS = "registrations";
    private static final String WAITLIST = "waitlist";
    private static final String USERS = "users";

    /** How many waiting-list slots one cancellation looks at when promoting the next student. */
    private static final int PROMOTION_SCAN_LIMIT = 30;

    public interface EventListener {
        void onEvent(Event event);

        void onMissing();

        void onError(String message);
    }

    public interface MyRegistrationsListener {
        void onChanged(List<MyRegistration> registrations);

        void onError(String message);
    }

    /** reg is null when the person has never registered for the event. */
    public interface MyRegistrationListener {
        void onChanged(MyRegistration reg);

        void onError(String message);
    }

    public interface ParticipantsListener {
        void onParticipants(List<Participant> participants);

        void onError(String message);
    }

    private static EventRepository instance;

    private final EventDao dao;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Executor io = Executors.newSingleThreadExecutor();

    private EventRepository(Context context) {
        dao = AppDatabase.getInstance(context).eventDao();
    }

    public static synchronized EventRepository getInstance(Context context) {
        if (instance == null) {
            instance = new EventRepository(context.getApplicationContext());
        }
        return instance;
    }

    // ---------------------------------------------------------------- event list (Room)

    public LiveData<List<EventEntity>> observeCachedEvents() {
        return dao.observeAll();
    }

    /** Keeps Room in step with the events collection. Remove the registration to stop. */
    public ListenerRegistration startSync(ClubRepository.SyncListener listener) {
        return db.collection(EVENTS).addSnapshotListener((snap, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            if (snap == null) {
                return;
            }
            // An empty answer from the cache must not wipe events we already saved.
            if (snap.getMetadata().isFromCache() && snap.isEmpty()) {
                listener.onOfflineEmpty();
                return;
            }
            List<EventEntity> entities = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Event e = toEvent(d);
                if (e != null) {
                    entities.add(EventEntity.fromEvent(e));
                }
            }
            io.execute(() -> dao.replaceAll(entities));
            listener.onSynced();
        });
    }

    // ---------------------------------------------------------------- single event / lists

    public ListenerRegistration listenEvent(String eventId, EventListener listener) {
        return db.collection(EVENTS).document(eventId).addSnapshotListener((doc, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            Event e = doc == null || !doc.exists() ? null : toEvent(doc);
            if (e == null) {
                listener.onMissing();
            } else {
                listener.onEvent(e);
            }
        });
    }

    /** One-time read, used to pre-fill the edit form. */
    public void loadEvent(String eventId, Callback<Event> cb) {
        db.collection(EVENTS).document(eventId).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                cb.onError(describe(task.getException()));
                return;
            }
            Event e = task.getResult().exists() ? toEvent(task.getResult()) : null;
            if (e == null) {
                cb.onError("This event no longer exists.");
            } else {
                cb.onSuccess(e);
            }
        });
    }

    public ListenerRegistration listenMyRegistrations(String uid, MyRegistrationsListener listener) {
        return mirrorCollection(uid).addSnapshotListener((snap, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            if (snap == null) {
                return;
            }
            List<MyRegistration> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                MyRegistration r = d.toObject(MyRegistration.class);
                if (r != null) {
                    r.setEventId(d.getId());
                    list.add(r);
                }
            }
            listener.onChanged(list);
        });
    }

    public ListenerRegistration listenMyRegistration(String uid, String eventId,
                                                     MyRegistrationListener listener) {
        return mirrorCollection(uid).document(eventId).addSnapshotListener((doc, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            if (doc == null || !doc.exists()) {
                listener.onChanged(null);
                return;
            }
            MyRegistration r = doc.toObject(MyRegistration.class);
            if (r != null) {
                r.setEventId(doc.getId());
            }
            listener.onChanged(r);
        });
    }

    /** For organizers: everyone registered or waitlisted for an event. */
    public ListenerRegistration listenParticipants(String eventId, ParticipantsListener listener) {
        return db.collection(EVENTS).document(eventId).collection(REGISTRATIONS)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    List<Participant> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Participant p = d.toObject(Participant.class);
                        if (p != null) {
                            p.setUid(d.getId());
                            list.add(p);
                        }
                    }
                    listener.onParticipants(list);
                });
    }

    // ---------------------------------------------------------------- create / edit events

    public void createEvent(Event e, String creatorUid, Callback<String> cb) {
        Map<String, Object> data = baseFields(e);
        data.put("clubId", e.getClubId());
        data.put("clubName", e.getClubName());
        data.put("organizers", e.getOrganizers() == null ? new ArrayList<String>() : e.getOrganizers());
        data.put("createdBy", creatorUid);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("registeredCount", 0L);
        data.put("waitlistCount", 0L);
        data.put("waitHead", 0L);
        data.put("waitTail", 0L);
        if (e.getRegistrationDeadline() != null) {
            data.put("registrationDeadline", e.getRegistrationDeadline());
        }
        db.collection(EVENTS).add(data).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                cb.onSuccess(task.getResult().getId());
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    public void updateEvent(String eventId, Event e, Callback<Void> cb) {
        Map<String, Object> data = baseFields(e);
        data.put("registrationDeadline", e.getRegistrationDeadline() == null
                ? FieldValue.delete() : e.getRegistrationDeadline());
        db.collection(EVENTS).document(eventId).update(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(null);
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    private Map<String, Object> baseFields(Event e) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", e.getTitle());
        data.put("description", e.getDescription());
        data.put("type", e.getType());
        data.put("tags", e.getTags() == null ? new ArrayList<String>() : e.getTags());
        data.put("startTime", e.getStartTime());
        data.put("endTime", e.getEndTime());
        data.put("venue", e.getVenue());
        data.put("capacity", e.getCapacity());
        data.put("teamSizeMin", e.getTeamSizeMin());
        data.put("teamSizeMax", e.getTeamSizeMax());
        data.put("prizes", e.getPrizes() == null ? "" : e.getPrizes());
        data.put("rules", e.getRules() == null ? "" : e.getRules());
        return data;
    }

    // ---------------------------------------------------------------- registration

    /**
     * Registers the student, or puts them on the waiting list if the event is full.
     * The callback receives "registered" or "waitlisted".
     */
    public void register(String eventId, UserProfile user, Callback<String> cb) {
        final DocumentReference eventRef = db.collection(EVENTS).document(eventId);
        final DocumentReference regRef = eventRef.collection(REGISTRATIONS).document(user.getUid());
        final DocumentReference mirrorRef = mirrorCollection(user.getUid()).document(eventId);

        db.runTransaction((Transaction.Function<String>) tx -> {
            DocumentSnapshot event = tx.get(eventRef);
            DocumentSnapshot reg = tx.get(regRef);

            if (!event.exists()) {
                throw new FirebaseFirestoreException("This event no longer exists.",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            String existing = reg.exists() ? reg.getString("status") : null;
            if (MyRegistration.REGISTERED.equals(existing)
                    || MyRegistration.WAITLISTED.equals(existing)) {
                return existing; // already in; nothing to do
            }

            long now = System.currentTimeMillis();
            Date start = event.getDate("startTime");
            Date deadline = event.getDate("registrationDeadline");
            if ((start != null && now >= start.getTime())
                    || (deadline != null && now > deadline.getTime())) {
                throw new FirebaseFirestoreException("Registration is closed for this event.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            long capacity = num(event, "capacity");
            long registered = num(event, "registeredCount");
            long waitTail = num(event, "waitTail");
            long waitCount = num(event, "waitlistCount");

            String status;
            long seq = -1;
            if (registered < capacity) {
                status = MyRegistration.REGISTERED;
                tx.update(eventRef, "registeredCount", registered + 1);
            } else {
                status = MyRegistration.WAITLISTED;
                seq = waitTail;
                Map<String, Object> entry = new HashMap<>();
                entry.put("uid", user.getUid());
                entry.put("seq", seq);
                tx.set(eventRef.collection(WAITLIST).document(String.valueOf(seq)), entry);
                tx.update(eventRef, "waitTail", waitTail + 1, "waitlistCount", waitCount + 1);
            }

            // A personal pass code: only the holder's phone and the organizer can see it.
            String passCode = Codes.random(10);

            Map<String, Object> regData = new HashMap<>();
            regData.put("uid", user.getUid());
            regData.put("passCode", passCode);
            regData.put("name", user.getName());
            regData.put("studentId", user.getStudentId());
            regData.put("department", user.getDepartment());
            regData.put("status", status);
            regData.put("updatedAt", FieldValue.serverTimestamp());
            if (seq >= 0) {
                regData.put("waitSeq", seq);
            }
            tx.set(regRef, regData);

            Map<String, Object> mirror = new HashMap<>();
            mirror.put("eventId", eventId);
            mirror.put("passCode", passCode);
            mirror.put("title", event.getString("title"));
            mirror.put("clubName", event.getString("clubName"));
            mirror.put("type", event.getString("type"));
            mirror.put("startTime", event.getDate("startTime"));
            mirror.put("endTime", event.getDate("endTime"));
            mirror.put("venue", event.getString("venue"));
            mirror.put("status", status);
            mirror.put("updatedAt", FieldValue.serverTimestamp());
            if (seq >= 0) {
                mirror.put("waitSeq", seq);
            }
            tx.set(mirrorRef, mirror);
            return status;
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(task.getResult());
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    /**
     * Cancels a registration or leaves the waiting list. If a seat was freed, the first student on
     * the waiting list is promoted in the same transaction. The callback receives "cancelled".
     */
    public void cancelRegistration(String eventId, String uid, Callback<String> cb) {
        final DocumentReference eventRef = db.collection(EVENTS).document(eventId);
        final DocumentReference regRef = eventRef.collection(REGISTRATIONS).document(uid);
        final DocumentReference mirrorRef = mirrorCollection(uid).document(eventId);

        db.runTransaction((Transaction.Function<String>) tx -> {
            // ---- all reads first
            DocumentSnapshot event = tx.get(eventRef);
            DocumentSnapshot reg = tx.get(regRef);
            if (!event.exists() || !reg.exists()) {
                return MyRegistration.CANCELLED;
            }
            String status = reg.getString("status");
            boolean wasRegistered = MyRegistration.REGISTERED.equals(status);
            boolean wasWaitlisted = MyRegistration.WAITLISTED.equals(status);
            if (!wasRegistered && !wasWaitlisted) {
                return MyRegistration.CANCELLED;
            }

            long registered = num(event, "registeredCount");
            long waitHead = num(event, "waitHead");
            long waitTail = num(event, "waitTail");
            long waitCount = num(event, "waitlistCount");

            List<DocumentSnapshot> candidates = new ArrayList<>();
            long scanEnd = waitHead;
            if (wasRegistered) {
                scanEnd = Math.min(waitTail, waitHead + PROMOTION_SCAN_LIMIT);
                for (long s = waitHead; s < scanEnd; s++) {
                    candidates.add(tx.get(eventRef.collection(WAITLIST).document(String.valueOf(s))));
                }
            }

            // ---- then writes
            FieldValue now = FieldValue.serverTimestamp();
            tx.update(regRef, "status", MyRegistration.CANCELLED, "updatedAt", now);
            tx.update(mirrorRef, "status", MyRegistration.CANCELLED, "updatedAt", now);

            if (wasWaitlisted) {
                long seq = num(reg, "waitSeq");
                tx.delete(eventRef.collection(WAITLIST).document(String.valueOf(seq)));
                tx.update(eventRef, "waitlistCount", Math.max(0, waitCount - 1));
                return MyRegistration.CANCELLED;
            }

            // A registered student left: promote the first person still waiting, if any.
            DocumentSnapshot promoted = null;
            long newHead = scanEnd;
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).exists()) {
                    promoted = candidates.get(i);
                    newHead = waitHead + i + 1;
                    break;
                }
            }

            if (promoted == null) {
                tx.update(eventRef, "registeredCount", Math.max(0, registered - 1),
                        "waitHead", newHead);
            } else {
                String promotedUid = promoted.getString("uid");
                tx.delete(promoted.getReference());
                tx.update(eventRef, "waitHead", newHead,
                        "waitlistCount", Math.max(0, waitCount - 1));
                tx.update(eventRef.collection(REGISTRATIONS).document(promotedUid),
                        "status", MyRegistration.REGISTERED, "updatedAt", now);
                tx.update(mirrorCollection(promotedUid).document(eventId),
                        "status", MyRegistration.REGISTERED, "updatedAt", now);
            }
            return MyRegistration.CANCELLED;
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(task.getResult());
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    /**
     * Gives an older registration (made before passes existed) its pass code.
     * The callback receives the code.
     */
    public void ensurePassCode(String eventId, String uid, Callback<String> cb) {
        final DocumentReference eventRef = db.collection(EVENTS).document(eventId);
        final DocumentReference regRef = eventRef.collection(REGISTRATIONS).document(uid);
        final DocumentReference mirrorRef = mirrorCollection(uid).document(eventId);

        db.runTransaction((Transaction.Function<String>) tx -> {
            DocumentSnapshot reg = tx.get(regRef);
            DocumentSnapshot mirror = tx.get(mirrorRef);
            if (!reg.exists() || !MyRegistration.REGISTERED.equals(reg.getString("status"))) {
                throw new FirebaseFirestoreException("You are not registered for this event.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            FieldValue now = FieldValue.serverTimestamp();
            String existing = reg.getString("passCode");
            if (existing != null && !existing.isEmpty()) {
                if (mirror.exists() && mirror.getString("passCode") == null) {
                    tx.update(mirrorRef, "passCode", existing);
                }
                return existing;
            }
            String code = Codes.random(10);
            tx.update(regRef, "passCode", code, "updatedAt", now);
            if (mirror.exists()) {
                tx.update(mirrorRef, "passCode", code, "updatedAt", now);
            }
            return code;
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(task.getResult());
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    /** One-time read of my own registration summary (used when the local copy is missing). */
    public void getMyRegistration(String uid, String eventId, Callback<MyRegistration> cb) {
        mirrorCollection(uid).document(eventId).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                cb.onError(describe(task.getException()));
                return;
            }
            MyRegistration r = task.getResult().toObject(MyRegistration.class);
            if (r != null) {
                r.setEventId(task.getResult().getId());
            }
            cb.onSuccess(r);
        });
    }

    // ---------------------------------------------------------------- helpers

    private com.google.firebase.firestore.CollectionReference mirrorCollection(String uid) {
        return db.collection(USERS).document(uid).collection(REGISTRATIONS);
    }

    private static Event toEvent(DocumentSnapshot doc) {
        Event e = doc.toObject(Event.class);
        if (e != null) {
            e.setId(doc.getId());
        }
        return e;
    }

    private static long num(DocumentSnapshot doc, String field) {
        Long v = doc.getLong(field);
        return v == null ? 0 : v;
    }

    private static String describe(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    || code == FirebaseFirestoreException.Code.NOT_FOUND) {
                return e.getMessage();
            }
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Not allowed. Check that the latest firestore.rules is published.";
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

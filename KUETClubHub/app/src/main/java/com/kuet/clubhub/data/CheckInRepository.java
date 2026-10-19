package com.kuet.clubhub.data;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.model.CheckIn;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.util.Codes;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Attendance: the organizer's rotating check-in token, and the check-in records at
 * events/{eventId}/checkins/{uid}. The Firestore rules decide whether a record is accepted
 * (token current, student registered, pass code correct), so a modified app cannot cheat.
 */
public final class CheckInRepository {

    private static final String EVENTS = "events";
    private static final String CHECKINS = "checkins";
    private static final String REGISTRATIONS = "registrations";
    private static final String TOKEN_COLLECTION = "checkinToken";
    private static final String TOKEN_DOC = "current";

    public static final String METHOD_ROTATING_QR = "rotating_qr";
    public static final String METHOD_PASS = "pass";
    public static final String METHOD_MANUAL = "manual";

    /** The outcome of the organizer scanning one student pass. */
    public static final class ScanResult {
        public static final int OK = 0;
        public static final int DUPLICATE = 1;
        public static final int NOT_REGISTERED = 2;
        public static final int WAITLISTED = 3;
        public static final int INVALID_PASS = 4;

        public final int status;
        public final String name;
        public final Date time;

        ScanResult(int status, @Nullable String name, @Nullable Date time) {
            this.status = status;
            this.name = name;
            this.time = time;
        }
    }

    public interface CheckInsListener {
        void onChanged(List<CheckIn> checkIns);

        void onError(String message);
    }

    /** checkIn is null when the person has not checked in. */
    public interface MyCheckInListener {
        void onChanged(@Nullable CheckIn checkIn);

        void onError(String message);
    }

    private CheckInRepository() {
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static DocumentReference checkInRef(String eventId, String uid) {
        return db().collection(EVENTS).document(eventId).collection(CHECKINS).document(uid);
    }

    private static DocumentReference tokenRef(String eventId) {
        return db().collection(EVENTS).document(eventId)
                .collection(TOKEN_COLLECTION).document(TOKEN_DOC);
    }

    // ---------------------------------------------------------------- live data

    public static ListenerRegistration listenCheckIns(String eventId, CheckInsListener listener) {
        return db().collection(EVENTS).document(eventId).collection(CHECKINS)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(describe(error));
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    List<CheckIn> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        CheckIn c = d.toObject(CheckIn.class);
                        if (c != null) {
                            c.setUid(d.getId());
                            list.add(c);
                        }
                    }
                    listener.onChanged(list);
                });
    }

    public static ListenerRegistration listenMyCheckIn(String eventId, String uid,
                                                       MyCheckInListener listener) {
        return checkInRef(eventId, uid).addSnapshotListener((doc, error) -> {
            if (error != null) {
                listener.onError(describe(error));
                return;
            }
            if (doc == null || !doc.exists()) {
                listener.onChanged(null);
                return;
            }
            CheckIn c = doc.toObject(CheckIn.class);
            if (c != null) {
                c.setUid(doc.getId());
            }
            listener.onChanged(c);
        });
    }

    // ---------------------------------------------------------------- organizer: rotating token

    public static String newToken() {
        return Codes.random(12);
    }

    /** Makes the token current. The server stamps the time, so phone clocks do not matter. */
    public static void publishToken(String eventId, String token, Callback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("updatedAt", FieldValue.serverTimestamp());
        tokenRef(eventId).set(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(null);
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    /** Best effort: empties the token so no code is valid once the organizer leaves the screen. */
    public static void closeCheckIn(String eventId) {
        Map<String, Object> data = new HashMap<>();
        data.put("token", "");
        data.put("updatedAt", FieldValue.serverTimestamp());
        tokenRef(eventId).set(data);
    }

    // ---------------------------------------------------------------- student: scan the organizer's code

    /** The callback receives "ok" or "duplicate". */
    public static void checkInWithToken(String eventId, String token, String uid,
                                        Callback<String> cb) {
        final DocumentReference ref = checkInRef(eventId, uid);
        ref.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                cb.onError(describe(task.getException()));
                return;
            }
            DocumentSnapshot existing = task.getResult();
            if (existing != null && existing.exists()) {
                cb.onSuccess("duplicate");
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("uid", uid);
            data.put("method", METHOD_ROTATING_QR);
            data.put("token", token);
            data.put("timestamp", FieldValue.serverTimestamp());
            ref.set(data).addOnCompleteListener(setTask -> {
                if (setTask.isSuccessful()) {
                    cb.onSuccess("ok");
                } else if (isPermissionDenied(setTask.getException())) {
                    cb.onError("That code has expired, or you are not registered for this event. "
                            + "Scan the code that is showing on the organizer's screen right now.");
                } else {
                    cb.onError(describe(setTask.getException()));
                }
            });
        });
    }

    // ---------------------------------------------------------------- organizer: scan a student's pass

    public static void checkInWithPass(String eventId, String uid, String passCode,
                                       String scannerUid, Callback<ScanResult> cb) {
        final DocumentReference regRef = db().collection(EVENTS).document(eventId)
                .collection(REGISTRATIONS).document(uid);
        final DocumentReference ref = checkInRef(eventId, uid);

        regRef.get().addOnCompleteListener(regTask -> {
            if (!regTask.isSuccessful()) {
                cb.onError(describe(regTask.getException()));
                return;
            }
            DocumentSnapshot reg = regTask.getResult();
            if (reg == null || !reg.exists()) {
                cb.onSuccess(new ScanResult(ScanResult.NOT_REGISTERED, null, null));
                return;
            }
            String name = reg.getString("name");
            String status = reg.getString("status");
            if (MyRegistration.WAITLISTED.equals(status)) {
                cb.onSuccess(new ScanResult(ScanResult.WAITLISTED, name, null));
                return;
            }
            if (!MyRegistration.REGISTERED.equals(status)) {
                cb.onSuccess(new ScanResult(ScanResult.NOT_REGISTERED, name, null));
                return;
            }
            String stored = reg.getString("passCode");
            if (stored == null || !stored.equals(passCode)) {
                cb.onSuccess(new ScanResult(ScanResult.INVALID_PASS, name, null));
                return;
            }

            ref.get().addOnCompleteListener(checkTask -> {
                if (!checkTask.isSuccessful()) {
                    cb.onError(describe(checkTask.getException()));
                    return;
                }
                DocumentSnapshot existing = checkTask.getResult();
                if (existing != null && existing.exists()) {
                    cb.onSuccess(new ScanResult(ScanResult.DUPLICATE, name,
                            existing.getDate("timestamp")));
                    return;
                }
                Map<String, Object> data = new HashMap<>();
                data.put("uid", uid);
                data.put("method", METHOD_PASS);
                data.put("passCode", passCode);
                data.put("scannedBy", scannerUid);
                data.put("timestamp", FieldValue.serverTimestamp());
                ref.set(data).addOnCompleteListener(setTask -> {
                    if (setTask.isSuccessful()) {
                        cb.onSuccess(new ScanResult(ScanResult.OK, name, new Date()));
                    } else {
                        cb.onError(describe(setTask.getException()));
                    }
                });
            });
        });
    }

    /** Organizer marks a registered student present by hand (for example a phone with a dead battery). */
    public static void manualCheckIn(String eventId, String uid, String scannerUid,
                                     Callback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("method", METHOD_MANUAL);
        data.put("scannedBy", scannerUid);
        data.put("timestamp", FieldValue.serverTimestamp());
        checkInRef(eventId, uid).set(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(null);
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isPermissionDenied(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }

    private static String describe(Exception e) {
        if (isPermissionDenied(e)) {
            return "Not allowed. Check that the latest firestore.rules is published.";
        }
        if (e instanceof FirebaseFirestoreException && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.UNAVAILABLE) {
            return "You need an internet connection for this.";
        }
        if (e != null && e.getMessage() != null) {
            return e.getMessage();
        }
        return "Something went wrong. Please try again.";
    }
}

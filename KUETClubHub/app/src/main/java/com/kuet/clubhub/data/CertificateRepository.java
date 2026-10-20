package com.kuet.clubhub.data;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;
import com.kuet.clubhub.model.Certificate;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.Codes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Certificates. certificates/{certId} is the public record that the Verify screen reads;
 * events/{eventId}/certIssued/{uid} points to it so a student gets exactly one certificate per
 * event. The rules only allow issuing for a student with a check-in record, after the event
 * has ended, and require the copied event and name fields to match the real documents.
 */
public final class CertificateRepository {

    private static final String CERTIFICATES = "certificates";
    private static final String EVENTS = "events";
    private static final String ISSUED = "certIssued";

    private static final int MAX_ID_ATTEMPTS = 3;

    private CertificateRepository() {
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static DocumentReference issuedRef(String eventId, String uid) {
        return db().collection(EVENTS).document(eventId).collection(ISSUED).document(uid);
    }

    /** A readable id such as KCH-7F3K-9QXA. */
    public static String newCertId() {
        return "KCH-" + Codes.random(4) + "-" + Codes.random(4);
    }

    /** Upper-cases and trims what the person typed. */
    public static String normalizeId(@Nullable String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    /** Returns the student's existing certificate for the event, or issues it. */
    public static void getOrIssue(Event event, UserProfile user, Callback<Certificate> cb) {
        issuedRef(event.getId(), user.getUid()).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                cb.onError(describe(task.getException()));
                return;
            }
            DocumentSnapshot index = task.getResult();
            if (index != null && index.exists() && index.getString("certId") != null) {
                verify(index.getString("certId"), new Callback<Certificate>() {
                    @Override
                    public void onSuccess(Certificate c) {
                        if (c == null) {
                            cb.onError("Your certificate record could not be found.");
                        } else {
                            cb.onSuccess(c);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
            } else {
                issue(event, user, 1, cb);
            }
        });
    }

    private static void issue(Event event, UserProfile user, int attempt, Callback<Certificate> cb) {
        final String certId = newCertId();
        Map<String, Object> cert = new HashMap<>();
        cert.put("eventId", event.getId());
        cert.put("uid", user.getUid());
        cert.put("type", Certificate.TYPE_PARTICIPATION);
        cert.put("name", user.getName());
        cert.put("studentId", user.getStudentId());
        cert.put("eventTitle", event.getTitle());
        cert.put("clubName", event.getClubName());
        cert.put("eventDate", event.getStartTime());
        cert.put("issuedAt", FieldValue.serverTimestamp());

        Map<String, Object> index = new HashMap<>();
        index.put("certId", certId);
        index.put("issuedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db().batch();
        batch.set(db().collection(CERTIFICATES).document(certId), cert);
        batch.set(issuedRef(event.getId(), user.getUid()), index);
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Read it back so the server's issue time is on the certificate.
                verify(certId, new Callback<Certificate>() {
                    @Override
                    public void onSuccess(Certificate c) {
                        if (c == null) {
                            cb.onError("Your certificate was saved but could not be read back.");
                        } else {
                            cb.onSuccess(c);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
            } else if (attempt < MAX_ID_ATTEMPTS && isPermissionDenied(task.getException())) {
                // The only denial we can fix by ourselves is an id that already exists.
                issue(event, user, attempt + 1, cb);
            } else {
                cb.onError(describe(task.getException()));
            }
        });
    }

    /** Looks a certificate up by id. The callback receives null when there is no such certificate. */
    public static void verify(String certId, Callback<Certificate> cb) {
        String id = normalizeId(certId);
        if (id.isEmpty() || id.contains("/")) {
            cb.onSuccess(null);
            return;
        }
        db().collection(CERTIFICATES).document(id).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                cb.onError(describe(task.getException()));
                return;
            }
            DocumentSnapshot doc = task.getResult();
            if (!doc.exists()) {
                cb.onSuccess(null);
                return;
            }
            Certificate c = doc.toObject(Certificate.class);
            if (c != null) {
                c.setCertId(doc.getId());
            }
            cb.onSuccess(c);
        });
    }

    private static boolean isPermissionDenied(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }

    private static String describe(Exception e) {
        if (isPermissionDenied(e)) {
            return "A certificate can only be issued after the event has ended, to a student "
                    + "whose attendance was verified. Check that the latest firestore.rules is published.";
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

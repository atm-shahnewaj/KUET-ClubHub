package com.kuet.clubhub.data;

import android.app.Activity;
import android.content.Context;
import android.util.Patterns;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.notify.AlertController;
import com.kuet.clubhub.util.NotificationPrefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gmail-only Authentication + user-profile operations (Firebase Auth Google OAuth + Gmail validation
 * and Firestore users/{uid}).
 */
public final class UserRepository {

    public static final String ERR_NO_PROFILE = "NO_PROFILE";
    /** Firebase rejected the app's signing certificate: its SHA-1/SHA-256 is not registered. */
    public static final String ERR_CERT_HASH = "CERT_HASH";
    private static final String USERS = "users";

    private UserRepository() {
    }

    /** Enforces that only valid @gmail.com (or @googlemail.com) addresses are accepted. */
    public static boolean isValidGmail(@Nullable String email) {
        if (email == null) {
            return false;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            return false;
        }
        return trimmed.endsWith("@gmail.com") || trimmed.endsWith("@googlemail.com");
    }

    /**
     * Launches Firebase Auth's Google OAuth sign-in flow (works out of the box with Firebase Auth
     * once Google Sign-In is enabled in Firebase Console -> Authentication -> Sign-in method).
     */
    public static void signInWithGoogle(Activity activity, Callback<FirebaseUser> cb) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        Task<AuthResult> pending = auth.getPendingAuthResult();
        if (pending != null) {
            pending.addOnSuccessListener(result -> {
                if (result != null && result.getUser() != null) {
                    cb.onSuccess(result.getUser());
                } else {
                    cb.onError("Google sign-in could not be completed.");
                }
            }).addOnFailureListener(e -> cb.onError(googleError(e)));
            return;
        }

        OAuthProvider.Builder provider = OAuthProvider.newBuilder("google.com");
        provider.addCustomParameter("prompt", "select_account");
        auth.startActivityForSignInWithProvider(activity, provider.build())
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result != null ? result.getUser() : auth.getCurrentUser();
                    if (user != null) {
                        cb.onSuccess(user);
                    } else {
                        cb.onError("Google sign-in did not return an account.");
                    }
                })
                .addOnFailureListener(e -> cb.onError(googleError(e)));
    }

    /** True when Firebase refused the sign-in because this build's certificate is not registered. */
    static boolean isCertHashError(@Nullable Exception e) {
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if (code != null && code.toUpperCase(Locale.ROOT).contains("CERT_HASH")) {
                return true;
            }
        }
        String msg = e == null || e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("cert_hash") || msg.contains("certificate hash")
                || msg.contains("sha-1") || msg.contains("sha1")
                || msg.contains("not authorized to use firebase authentication");
    }

    private static String googleError(@Nullable Exception e) {
        return isCertHashError(e) ? ERR_CERT_HASH : authError(e);
    }

    /**
     * Saves the KUET student/organizer profile in Firestore for an already-authenticated Google user.
     */
    public static void saveGoogleUserProfile(FirebaseUser user, String name, String studentId,
                                             String department, int year, Role role,
                                             Callback<UserProfile> cb) {
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        UserProfile profile = new UserProfile();
        profile.setUid(user.getUid());
        profile.setName(name);
        profile.setStudentId(studentId);
        profile.setDepartment(department);
        profile.setYear(year);
        profile.setEmail(email);
        profile.setRole(role.getValue());
        profile.setPhotoBase64("");

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("studentId", studentId);
        data.put("department", department);
        data.put("year", year);
        data.put("email", email);
        data.put("role", role.getValue());
        data.put("points", 0L);
        data.put("photoBase64", "");
        data.put("skills", new ArrayList<String>());
        data.put("interests", new ArrayList<String>());
        data.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection(USERS).document(user.getUid())
                .set(data)
                .addOnCompleteListener(saveTask -> {
                    if (saveTask.isSuccessful()) {
                        cb.onSuccess(profile);
                    } else {
                        cb.onError("Your profile could not be saved. "
                                + firestoreError(saveTask.getException()));
                    }
                });
    }

    public static void register(String name, String studentId, String department, int year,
                                String email, String password, Role role,
                                Callback<UserProfile> cb) {
        register(name, studentId, department, year, email, password, role, null, cb);
    }

    public static void register(String name, String studentId, String department, int year,
                                String email, String password, Role role,
                                @Nullable String photoBase64,
                                Callback<UserProfile> cb) {
        if (!isValidGmail(email)) {
            cb.onError("Only @gmail.com accounts are allowed.");
            return;
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null
                    || task.getResult().getUser() == null) {
                cb.onError(authError(task.getException()));
                return;
            }
            FirebaseUser user = task.getResult().getUser();

            UserProfile profile = new UserProfile();
            profile.setUid(user.getUid());
            profile.setName(name);
            profile.setStudentId(studentId);
            profile.setDepartment(department);
            profile.setYear(year);
            profile.setEmail(email.trim());
            profile.setRole(role.getValue());
            profile.setPhotoBase64(photoBase64);

            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("studentId", studentId);
            data.put("department", department);
            data.put("year", year);
            data.put("email", email.trim());
            data.put("role", role.getValue());
            data.put("points", 0L);
            data.put("photoBase64", photoBase64 == null ? "" : photoBase64);
            data.put("skills", new ArrayList<String>());
            data.put("interests", new ArrayList<String>());
            data.put("createdAt", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance().collection(USERS).document(user.getUid())
                    .set(data)
                    .addOnCompleteListener(saveTask -> {
                        if (saveTask.isSuccessful()) {
                            cb.onSuccess(profile);
                        } else {
                            user.delete().addOnCompleteListener(d -> auth.signOut());
                            cb.onError("Your account could not be set up. "
                                    + firestoreError(saveTask.getException()));
                        }
                    });
        });
    }

    public static void login(String email, String password, Callback<UserProfile> cb) {
        if (!isValidGmail(email)) {
            cb.onError("Only @gmail.com accounts are allowed.");
            return;
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener(task -> {
            FirebaseUser user = auth.getCurrentUser();
            if (!task.isSuccessful() || user == null) {
                cb.onError(authError(task.getException()));
                return;
            }
            fetchProfile(user.getUid(), new Callback<UserProfile>() {
                @Override
                public void onSuccess(UserProfile result) {
                    cb.onSuccess(result);
                }

                @Override
                public void onError(String message) {
                    if (ERR_NO_PROFILE.equals(message)) {
                        cb.onError(ERR_NO_PROFILE);
                    } else {
                        auth.signOut();
                        cb.onError(message);
                    }
                }
            });
        });
    }

    public static void fetchProfile(String uid, Callback<UserProfile> cb) {
        FirebaseFirestore.getInstance().collection(USERS).document(uid).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        cb.onError(firestoreError(task.getException()));
                        return;
                    }
                    DocumentSnapshot doc = task.getResult();
                    if (doc == null || !doc.exists()) {
                        cb.onError(ERR_NO_PROFILE);
                        return;
                    }
                    UserProfile profile = doc.toObject(UserProfile.class);
                    if (profile == null) {
                        cb.onError("Your profile could not be read.");
                        return;
                    }
                    profile.setUid(uid);
                    cb.onSuccess(profile);
                });
    }

    public static void sendPasswordReset(String email, Callback<Void> cb) {
        if (!isValidGmail(email)) {
            cb.onError("Enter a valid @gmail.com address.");
            return;
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(null);
            } else {
                cb.onError(authError(task.getException()));
            }
        });
    }

    /** Saves the editable profile fields (never the role or points; the rules forbid that). */
    public static void updateProfile(String uid, String name, String department, int year,
                                     List<String> skills, List<String> interests,
                                     Callback<Void> cb) {
        updateProfile(uid, name, department, year, skills, interests, null, cb);
    }

    public static void updateProfile(String uid, String name, String department, int year,
                                     List<String> skills, List<String> interests,
                                     @Nullable String photoBase64,
                                     Callback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("department", department);
        data.put("year", year);
        data.put("skills", skills);
        data.put("interests", interests);
        if (photoBase64 != null) {
            data.put("photoBase64", photoBase64);
        }
        FirebaseFirestore.getInstance().collection(USERS).document(uid).update(data)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        cb.onSuccess(null);
                    } else {
                        cb.onError(firestoreError(task.getException()));
                    }
                });
    }

    public static void logout(Context context) {
        AlertController.stopAll(context);
        ScheduleSync.clearAll(context);
        NotificationPrefs.clearSession(context);
        FirebaseAuth.getInstance().signOut();
        new SessionManager(context).clear();
    }

    static String authError(Exception e) {
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "That password is too weak. Use at least 6 characters.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "An account with this Gmail address already exists.";
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "No account was found for this Gmail address, or it has been disabled.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect Gmail credentials.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "No internet connection. Check your network and try again.";
        }
        if (e != null && e.getMessage() != null) {
            return e.getMessage();
        }
        return "Authentication could not be completed. Please try again.";
    }

    static String firestoreError(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Permission denied by Firestore. Make sure firestore.rules is published.";
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

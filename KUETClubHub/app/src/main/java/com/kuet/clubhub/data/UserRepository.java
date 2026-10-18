package com.kuet.clubhub.data;

import android.content.Context;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
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
import java.util.Map;

/** Authentication + user-profile operations (Firebase Auth and Firestore users/{uid}). */
public final class UserRepository {

    private static final String USERS = "users";

    private UserRepository() {
    }

    public static void register(String name, String studentId, String department, int year,
                                String email, String password, Role role,
                                Callback<UserProfile> cb) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
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
            profile.setEmail(email);
            profile.setRole(role.getValue());

            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("studentId", studentId);
            data.put("department", department);
            data.put("year", year);
            data.put("email", email);
            data.put("role", role.getValue());
            data.put("points", 0L);
            data.put("skills", new ArrayList<String>());
            data.put("interests", new ArrayList<String>());
            data.put("createdAt", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance().collection(USERS).document(user.getUid())
                    .set(data)
                    .addOnCompleteListener(saveTask -> {
                        if (saveTask.isSuccessful()) {
                            cb.onSuccess(profile);
                        } else {
                            // Undo the half-finished sign-up so the person can try again.
                            user.delete().addOnCompleteListener(d -> auth.signOut());
                            cb.onError("Your account could not be set up. "
                                    + firestoreError(saveTask.getException()));
                        }
                    });
        });
    }

    public static void login(String email, String password, Callback<UserProfile> cb) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
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
                    auth.signOut();
                    cb.onError(message);
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
                        cb.onError("No profile was found for this account.");
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
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(task -> {
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
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("department", department);
        data.put("year", year);
        data.put("skills", skills);
        data.put("interests", interests);
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
        // Stop everything that belongs to the signed-in student before forgetting them.
        AlertController.stopAll(context);
        ScheduleSync.clearAll(context);
        NotificationPrefs.clearSession(context);
        FirebaseAuth.getInstance().signOut();
        new SessionManager(context).clear();
    }

    static String authError(Exception e) {
        // WeakPassword extends InvalidCredentials, so it must be checked first.
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "That password is too weak. Use at least 6 characters.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "An account with this email already exists.";
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "No account was found for this email, or it has been disabled.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "No internet connection. Check your network and try again.";
        }
        if (e != null && e.getMessage() != null) {
            return e.getMessage();
        }
        return "Something went wrong. Please try again.";
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

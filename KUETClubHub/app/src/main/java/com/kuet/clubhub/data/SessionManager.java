package com.kuet.clubhub.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.kuet.clubhub.model.UserProfile;

/**
 * Keeps a small copy of the signed-in user's profile on the device so the app can
 * pick the right screens (by role) immediately, even before the network answers.
 */
public class SessionManager {

    private static final String PREFS = "clubhub_session";
    private static final String K_UID = "uid";
    private static final String K_NAME = "name";
    private static final String K_STUDENT_ID = "student_id";
    private static final String K_DEPARTMENT = "department";
    private static final String K_EMAIL = "email";
    private static final String K_ROLE = "role";
    private static final String K_YEAR = "year";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(UserProfile p) {
        prefs.edit()
                .putString(K_UID, p.getUid())
                .putString(K_NAME, p.getName())
                .putString(K_STUDENT_ID, p.getStudentId())
                .putString(K_DEPARTMENT, p.getDepartment())
                .putString(K_EMAIL, p.getEmail())
                .putString(K_ROLE, p.getRole())
                .putInt(K_YEAR, p.getYear())
                .apply();
    }

    /** Returns the cached profile only if it belongs to the given uid. */
    public UserProfile getCachedProfile(String uid) {
        String saved = prefs.getString(K_UID, null);
        if (saved == null || !saved.equals(uid)) {
            return null;
        }
        return build(saved);
    }

    /** Returns the cached profile, or null if nobody is cached. */
    public UserProfile getProfile() {
        String saved = prefs.getString(K_UID, null);
        return saved == null ? null : build(saved);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    private UserProfile build(String uid) {
        UserProfile p = new UserProfile();
        p.setUid(uid);
        p.setName(prefs.getString(K_NAME, ""));
        p.setStudentId(prefs.getString(K_STUDENT_ID, ""));
        p.setDepartment(prefs.getString(K_DEPARTMENT, ""));
        p.setEmail(prefs.getString(K_EMAIL, ""));
        p.setRole(prefs.getString(K_ROLE, "student"));
        p.setYear(prefs.getInt(K_YEAR, 0));
        return p;
    }
}

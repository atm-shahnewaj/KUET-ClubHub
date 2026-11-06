package com.kuet.clubhub.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.kuet.clubhub.model.UserProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private static final String K_PHOTO = "photo_base64";
    private static final String K_SKILLS = "skills";
    private static final String K_INTERESTS = "interests";

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
                .putString(K_PHOTO, p.getPhotoBase64())
                .putString(K_SKILLS, join(p.getSkills()))
                .putString(K_INTERESTS, join(p.getInterests()))
                .apply();
    }

    public UserProfile getCachedProfile(String uid) {
        String saved = prefs.getString(K_UID, null);
        if (saved == null || !saved.equals(uid)) {
            return null;
        }
        return build(saved);
    }

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
        p.setPhotoBase64(prefs.getString(K_PHOTO, null));
        p.setSkills(split(prefs.getString(K_SKILLS, "")));
        p.setInterests(split(prefs.getString(K_INTERESTS, "")));
        return p;
    }

    private static String join(List<String> list) {
        return list == null ? "" : TextUtils.join(",", list);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(csv.split(",")));
    }
}

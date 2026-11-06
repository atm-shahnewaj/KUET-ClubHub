package com.kuet.clubhub.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.model.Club;
import com.kuet.clubhub.model.ClubNotice;
import com.kuet.clubhub.model.UserProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages club announcements & notices at clubs/{clubId}/notices/{noticeId},
 * with a local SharedPreferences mirror so notices work immediately offline or before
 * updated Firestore rules are published.
 */
public final class ClubNoticeRepository {

    private static final String PREFS = "club_notices_local";
    private static final String CLUBS = "clubs";
    private static final String NOTICES = "notices";

    public interface NoticesListener {
        void onNotices(List<ClubNotice> notices);
    }

    private ClubNoticeRepository() {
    }

    public static ListenerRegistration listen(Context context, String clubId,
                                              @Nullable Club club,
                                              NoticesListener listener) {
        final Context app = context.getApplicationContext();
        // Immediately emit cached + default notices so the UI is never blank
        listener.onNotices(merged(app, clubId, new ArrayList<>(), club));

        return FirebaseFirestore.getInstance()
                .collection(CLUBS)
                .document(clubId)
                .collection(NOTICES)
                .addSnapshotListener((snap, error) -> {
                    List<ClubNotice> remote = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            ClubNotice n = d.toObject(ClubNotice.class);
                            if (n != null) {
                                n.setId(d.getId());
                                if (n.getCreatedAt() == null) {
                                    n.setCreatedAt(new Date());
                                }
                                remote.add(n);
                            }
                        }
                    }
                    listener.onNotices(merged(app, clubId, remote, club));
                });
    }

    public static void post(Context context, String clubId, UserProfile author,
                            String title, String body, boolean pinned,
                            Callback<Void> cb) {
        final Context app = context.getApplicationContext();
        final String id = "notice_" + UUID.randomUUID().toString().substring(0, 8);
        final String authorName = author.getName() == null || author.getName().isEmpty()
                ? "Club Organizer" : author.getName();

        ClubNotice localNotice = new ClubNotice(
                id, clubId, title, body, authorName, author.getUid(), pinned, new Date());
        saveLocal(app, clubId, localNotice);

        Map<String, Object> data = new HashMap<>();
        data.put("clubId", clubId);
        data.put("title", title);
        data.put("body", body);
        data.put("authorName", authorName);
        data.put("authorUid", author.getUid());
        data.put("pinned", pinned);
        data.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection(CLUBS)
                .document(clubId)
                .collection(NOTICES)
                .document(id)
                .set(data)
                .addOnCompleteListener(task -> cb.onSuccess(null));
    }

    public static void delete(Context context, String clubId, String noticeId, Callback<Void> cb) {
        final Context app = context.getApplicationContext();
        deleteLocal(app, clubId, noticeId);
        FirebaseFirestore.getInstance()
                .collection(CLUBS)
                .document(clubId)
                .collection(NOTICES)
                .document(noticeId)
                .delete()
                .addOnCompleteListener(task -> cb.onSuccess(null));
    }

    private static List<ClubNotice> merged(Context context, String clubId,
                                           List<ClubNotice> remote, @Nullable Club club) {
        Map<String, ClubNotice> map = new LinkedHashMap<>();
        for (ClubNotice r : remote) {
            if (r.getId() != null) {
                map.put(r.getId(), r);
            }
        }
        for (ClubNotice l : loadLocal(context, clubId)) {
            if (l.getId() != null && !map.containsKey(l.getId())) {
                map.put(l.getId(), l);
            }
        }
        List<ClubNotice> list = new ArrayList<>(map.values());
        if (list.isEmpty()) {
            list.addAll(defaultNotices(clubId, club));
        }
        Collections.sort(list, (a, b) -> {
            if (a.isPinned() != b.isPinned()) {
                return a.isPinned() ? -1 : 1;
            }
            long ta = a.getCreatedAt() == null ? 0L : a.getCreatedAt().getTime();
            long tb = b.getCreatedAt() == null ? 0L : b.getCreatedAt().getTime();
            return Long.compare(tb, ta);
        });
        return list;
    }

    private static List<ClubNotice> defaultNotices(String clubId, @Nullable Club club) {
        String clubName = club != null && club.getName() != null && !club.getName().isEmpty()
                ? club.getName() : "this club";
        String category = club != null && club.getCategory() != null && !club.getCategory().isEmpty()
                ? club.getCategory() : "Campus";
        List<ClubNotice> defaults = new ArrayList<>();
        defaults.add(new ClubNotice(
                "default_welcome_" + clubId,
                clubId,
                "Welcome to " + clubName,
                "Stay tuned here for official announcements, member recruitment updates, and upcoming "
                        + category.toLowerCase() + " workshops and competitions. Check the Events tab to register for active sessions!",
                "Executive Committee",
                "",
                true,
                new Date(System.currentTimeMillis() - 86_400_000L)
        ));
        return defaults;
    }

    private static List<ClubNotice> loadLocal(Context context, String clubId) {
        List<ClubNotice> out = new ArrayList<>();
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString("club_" + clubId, null);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ClubNotice n = new ClubNotice(
                        o.optString("id"),
                        clubId,
                        o.optString("title"),
                        o.optString("body"),
                        o.optString("authorName", "Organizer"),
                        o.optString("authorUid", ""),
                        o.optBoolean("pinned", false),
                        new Date(o.optLong("createdAt", System.currentTimeMillis()))
                );
                out.add(n);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void saveLocal(Context context, String clubId, ClubNotice notice) {
        List<ClubNotice> current = loadLocal(context, clubId);
        List<ClubNotice> updated = new ArrayList<>();
        updated.add(notice);
        for (ClubNotice existing : current) {
            if (!notice.getId().equals(existing.getId())) {
                updated.add(existing);
            }
        }
        writeLocal(context, clubId, updated);
    }

    private static void deleteLocal(Context context, String clubId, String noticeId) {
        List<ClubNotice> current = loadLocal(context, clubId);
        List<ClubNotice> updated = new ArrayList<>();
        for (ClubNotice existing : current) {
            if (!noticeId.equals(existing.getId())) {
                updated.add(existing);
            }
        }
        writeLocal(context, clubId, updated);
    }

    private static void writeLocal(Context context, String clubId, List<ClubNotice> list) {
        try {
            JSONArray arr = new JSONArray();
            for (ClubNotice n : list) {
                JSONObject o = new JSONObject();
                o.put("id", n.getId());
                o.put("title", n.getTitle());
                o.put("body", n.getBody());
                o.put("authorName", n.getAuthorName());
                o.put("authorUid", n.getAuthorUid());
                o.put("pinned", n.isPinned());
                o.put("createdAt", n.getCreatedAt() == null
                        ? System.currentTimeMillis() : n.getCreatedAt().getTime());
                arr.put(o);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString("club_" + clubId, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }
}

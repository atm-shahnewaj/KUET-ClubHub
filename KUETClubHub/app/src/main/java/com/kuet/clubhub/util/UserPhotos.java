package com.kuet.clubhub.util;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.FirebaseFirestore;
import com.kuet.clubhub.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up other users' profile pictures (users/{uid}.photoBase64) once, keeps them in memory,
 * and binds them to round avatars in chat, member lists, participant lists and so on.
 * Falls back to the user's initial letter when there is no photo.
 */
public final class UserPhotos {

    /** Cached result per uid; an empty string means "this user has no photo". */
    private static final Map<String, String> CACHE = new HashMap<>();
    private static final Map<String, List<Runnable>> WAITING = new HashMap<>();

    private UserPhotos() {
    }

    /** Forget a user's cached photo (call after the user changes their own picture). */
    public static void invalidate(@Nullable String uid) {
        if (uid != null) {
            CACHE.remove(uid);
        }
    }

    /** Remember a known photo without a network round trip. */
    public static void put(@Nullable String uid, @Nullable String photoBase64) {
        if (uid != null && !uid.isEmpty()) {
            CACHE.put(uid, photoBase64 == null ? "" : photoBase64);
        }
    }

    /**
     * Shows the initial immediately (or the cached photo if we have it) and swaps in the photo as
     * soon as it has been loaded. Safe to call from RecyclerView binds: a recycled view that now
     * belongs to another user is never updated with the old user's picture.
     */
    public static void bind(ImageView photo, @Nullable TextView initial,
                            @Nullable String uid, @Nullable String name) {
        String letter = name == null || name.trim().isEmpty()
                ? "?" : name.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        if (initial != null) {
            initial.setText(letter);
        }
        final String key = uid == null ? "" : uid;
        photo.setTag(R.id.tag_avatar_uid, key);

        if (key.isEmpty()) {
            showInitial(photo, initial);
            return;
        }
        String cached = CACHE.get(key);
        if (cached != null) {
            apply(photo, initial, cached);
            return;
        }
        showInitial(photo, initial);
        fetch(key, () -> {
            if (key.equals(photo.getTag(R.id.tag_avatar_uid))) {
                apply(photo, initial, CACHE.get(key));
            }
        });
    }

    private static void apply(ImageView photo, @Nullable TextView initial, @Nullable String base64) {
        Bitmap bmp = ImageHelper.decode(base64);
        if (bmp == null) {
            showInitial(photo, initial);
            return;
        }
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImageBitmap(bmp);
        photo.setVisibility(View.VISIBLE);
        if (initial != null) {
            initial.setVisibility(View.INVISIBLE);
        }
    }

    private static void showInitial(ImageView photo, @Nullable TextView initial) {
        photo.setImageDrawable(null);
        photo.setVisibility(View.GONE);
        if (initial != null) {
            initial.setVisibility(View.VISIBLE);
        }
    }

    private static void fetch(String uid, Runnable whenDone) {
        List<Runnable> list = WAITING.get(uid);
        if (list != null) {
            list.add(whenDone);
            return;
        }
        list = new ArrayList<>();
        list.add(whenDone);
        WAITING.put(uid, list);
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnCompleteListener(task -> {
                    String photo = "";
                    if (task.isSuccessful() && task.getResult() != null) {
                        String p = task.getResult().getString("photoBase64");
                        photo = p == null ? "" : p;
                        CACHE.put(uid, photo);
                    }
                    // On failure nothing is cached, so the next bind retries.
                    List<Runnable> done = WAITING.remove(uid);
                    if (done != null) {
                        for (Runnable r : done) {
                            if (CACHE.containsKey(uid)) {
                                r.run();
                            }
                        }
                    }
                });
    }
}

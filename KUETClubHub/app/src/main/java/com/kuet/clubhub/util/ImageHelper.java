package com.kuet.clubhub.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.kuet.clubhub.R;
import com.kuet.clubhub.model.EventType;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Handles picking, downscaling, compressing, caching, and binding images for:
 * 1) User Profile Pictures (square 256x256, with default user avatar fallback)
 * 2) Club Logos (square 256x256, with default club logo fallback)
 * 3) Event Banners (16:9 640x360, with default event banner fallback)
 */
public final class ImageHelper {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(60);

    private ImageHelper() {
    }

    /** Reads a content Uri on a background thread, center-crops to target size, and returns Base64. */
    @Nullable
    public static String encodeUriToBase64(Context context, Uri uri, int targetW, int targetH, int quality) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            Bitmap raw = BitmapFactory.decodeStream(in);
            if (raw == null) return null;
            Bitmap cropped = centerCrop(raw, targetW, targetH);
            if (cropped != raw) raw.recycle();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, out);
            cropped.recycle();
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static Bitmap decode(@Nullable String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return null;
        }
        String key = Integer.toHexString(base64.hashCode()) + "_" + base64.length();
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null) {
                CACHE.put(key, bmp);
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /** Binds a user profile picture; falls back to the default avatar drawable or initial text. */
    public static void bindUserAvatar(ImageView imageView, @Nullable TextView fallbackInitial,
                                      @Nullable String photoBase64, @Nullable String name) {
        Bitmap bmp = decode(photoBase64);
        if (bmp != null) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageBitmap(bmp);
            if (fallbackInitial != null) fallbackInitial.setVisibility(View.GONE);
        } else {
            imageView.setVisibility(View.VISIBLE);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageResource(R.drawable.ic_default_user_avatar);
            if (fallbackInitial != null) fallbackInitial.setVisibility(View.GONE);
        }
    }

    /** Binds a Club Logo; if none is uploaded, displays the default Club Logo. */
    public static void bindClubLogo(ImageView imageView, @Nullable String logoBase64) {
        Bitmap bmp = decode(logoBase64);
        if (bmp != null) {
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageBitmap(bmp);
        } else {
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setImageResource(R.drawable.ic_default_club_logo);
        }
    }

    /** Binds an Event Banner; if none is uploaded, assigns a default banner matching the event type. */
    public static void bindEventBanner(ImageView imageView, @Nullable String bannerBase64,
                                       @Nullable EventType type) {
        Bitmap bmp = decode(bannerBase64);
        if (bmp != null) {
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageBitmap(bmp);
        } else {
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageResource(defaultBannerRes(type));
        }
    }

    @DrawableRes
    public static int defaultBannerRes(@Nullable EventType type) {
        if (type == EventType.COMPETITION) {
            return R.drawable.bg_default_event_competition;
        } else if (type == EventType.WORKSHOP) {
            return R.drawable.bg_default_event_tech;
        }
        return R.drawable.bg_default_event_banner;
    }

    private static Bitmap centerCrop(Bitmap src, int targetW, int targetH) {
        int w = src.getWidth();
        int h = src.getHeight();
        float scale = Math.max((float) targetW / w, (float) targetH / h);
        int scaledW = Math.round(scale * w);
        int scaledH = Math.round(scale * h);
        Bitmap scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true);
        int x = Math.max(0, (scaledW - targetW) / 2);
        int y = Math.max(0, (scaledH - targetH) / 2);
        Bitmap result = Bitmap.createBitmap(scaled, x, y,
                Math.min(targetW, scaledW - x), Math.min(targetH, scaledH - y));
        if (result != scaled) scaled.recycle();
        return result;
    }
}

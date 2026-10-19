package com.kuet.clubhub.util;

import androidx.annotation.Nullable;

/**
 * The text stored inside ClubHub QR codes.
 *
 *   clubhub:v1:event:{eventId}:{token}            the organizer's rotating check-in code
 *   clubhub:v1:pass:{eventId}:{uid}:{passCode}    a student's personal event pass
 *
 * Firestore ids, uids and our random codes never contain a colon, so splitting is safe.
 */
public final class QrPayload {

    public static final int TYPE_EVENT_TOKEN = 1;
    public static final int TYPE_PASS = 2;

    private static final String PREFIX = "clubhub:v1:";

    public final int type;
    public final String eventId;
    public final String uid;      // passes only
    public final String secret;   // token or pass code

    private QrPayload(int type, String eventId, String uid, String secret) {
        this.type = type;
        this.eventId = eventId;
        this.uid = uid;
        this.secret = secret;
    }

    public static String forEventToken(String eventId, String token) {
        return PREFIX + "event:" + eventId + ":" + token;
    }

    public static String forPass(String eventId, String uid, String passCode) {
        return PREFIX + "pass:" + eventId + ":" + uid + ":" + passCode;
    }

    /** Returns null when the text is not a ClubHub code. */
    @Nullable
    public static QrPayload parse(@Nullable String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = raw.substring(PREFIX.length()).split(":");
        if (parts.length == 3 && "event".equals(parts[0])) {
            return new QrPayload(TYPE_EVENT_TOKEN, parts[1], null, parts[2]);
        }
        if (parts.length == 4 && "pass".equals(parts[0])) {
            return new QrPayload(TYPE_PASS, parts[1], parts[2], parts[3]);
        }
        return null;
    }
}

package com.kuet.clubhub.util;

import androidx.annotation.Nullable;

/**
 * The text stored inside ClubHub QR codes.
 *
 *   clubhub:v1:event:{eventId}:{token}            the organizer's rotating check-in code
 *   clubhub:v1:pass:{eventId}:{uid}:{passCode}    a student's personal event pass
 *   clubhub:v1:team:{teamId}:{inviteCode}         a team's invite code
 *   clubhub:v1:cert:{certId}                      a certificate, for the Verify screen
 *
 * Firestore ids, uids and our random codes never contain a colon, so splitting is safe.
 */
public final class QrPayload {

    public static final int TYPE_EVENT_TOKEN = 1;
    public static final int TYPE_PASS = 2;
    public static final int TYPE_TEAM = 3;
    public static final int TYPE_CERT = 4;

    private static final String PREFIX = "clubhub:v1:";

    public final int type;
    public final String eventId;  // check-in codes and passes
    public final String uid;      // passes only
    public final String secret;   // token, pass code, team invite code or certificate id
    public final String teamId;   // team codes only

    private QrPayload(int type, String eventId, String uid, String secret, String teamId) {
        this.type = type;
        this.eventId = eventId;
        this.uid = uid;
        this.secret = secret;
        this.teamId = teamId;
    }

    public static String forEventToken(String eventId, String token) {
        return PREFIX + "event:" + eventId + ":" + token;
    }

    public static String forPass(String eventId, String uid, String passCode) {
        return PREFIX + "pass:" + eventId + ":" + uid + ":" + passCode;
    }

    public static String forTeam(String teamId, String inviteCode) {
        return PREFIX + "team:" + teamId + ":" + inviteCode;
    }

    public static String forCertificate(String certId) {
        return PREFIX + "cert:" + certId;
    }

    /** Returns null when the text is not a ClubHub code. */
    @Nullable
    public static QrPayload parse(@Nullable String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = raw.substring(PREFIX.length()).split(":");
        if (parts.length == 3 && "event".equals(parts[0])) {
            return new QrPayload(TYPE_EVENT_TOKEN, parts[1], null, parts[2], null);
        }
        if (parts.length == 4 && "pass".equals(parts[0])) {
            return new QrPayload(TYPE_PASS, parts[1], parts[2], parts[3], null);
        }
        if (parts.length == 3 && "team".equals(parts[0])) {
            return new QrPayload(TYPE_TEAM, null, null, parts[2], parts[1]);
        }
        if (parts.length == 2 && "cert".equals(parts[0])) {
            return new QrPayload(TYPE_CERT, null, null, parts[1], null);
        }
        return null;
    }
}

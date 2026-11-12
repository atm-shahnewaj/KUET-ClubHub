package com.kuet.clubhub.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Notification settings chosen by the person, plus the "only events created after this" baseline
 * and per-club chat notification mute switches.
 */
public final class NotificationPrefs {

    private static final String PREFS = "clubhub_notifications";
    private static final String K_REMINDERS = "reminders_enabled";
    private static final String K_ALERTS = "alerts_enabled";
    private static final String K_ASKED = "asked_permission";
    private static final String K_BASELINE = "alert_baseline";
    private static final String K_CLUB_CHAT_MUTED_PREFIX = "club_chat_muted_";

    private NotificationPrefs() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean remindersEnabled(Context c) {
        return prefs(c).getBoolean(K_REMINDERS, true);
    }

    public static void setRemindersEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(K_REMINDERS, enabled).apply();
    }

    public static boolean alertsEnabled(Context c) {
        return prefs(c).getBoolean(K_ALERTS, true);
    }

    public static void setAlertsEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(K_ALERTS, enabled).apply();
    }

    public static boolean askedPermission(Context c) {
        return prefs(c).getBoolean(K_ASKED, false);
    }

    public static void setAskedPermission(Context c) {
        prefs(c).edit().putBoolean(K_ASKED, true).apply();
    }

    /** True when the user has muted chat/mention notifications and toasts for a specific club. */
    public static boolean isClubChatMuted(Context c, String clubId) {
        if (clubId == null || clubId.isEmpty()) {
            return false;
        }
        return prefs(c).getBoolean(K_CLUB_CHAT_MUTED_PREFIX + clubId, false);
    }

    public static void setClubChatMuted(Context c, String clubId, boolean muted) {
        if (clubId == null || clubId.isEmpty()) {
            return;
        }
        prefs(c).edit().putBoolean(K_CLUB_CHAT_MUTED_PREFIX + clubId, muted).apply();
    }

    /** Events created before this moment never trigger an interest alert. */
    public static long baseline(Context c) {
        return prefs(c).getLong(K_BASELINE, 0);
    }

    public static void ensureBaseline(Context c) {
        if (baseline(c) == 0) {
            resetBaseline(c);
        }
    }

    public static void resetBaseline(Context c) {
        prefs(c).edit().putLong(K_BASELINE, System.currentTimeMillis()).apply();
    }

    /** Called on logout. The on/off switches are kept; the baseline starts fresh next login. */
    public static void clearSession(Context c) {
        prefs(c).edit().remove(K_BASELINE).apply();
    }
}

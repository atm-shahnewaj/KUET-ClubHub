package com.kuet.clubhub.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.data.local.ScheduledEventEntity;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.util.NotificationPrefs;

import java.util.List;

/**
 * Sets and cancels the reminder alarms (one day before and one hour before an event) that wake
 * {@link ReminderReceiver}. Alarms are "inexact while idle", so they can be a few minutes late in
 * Doze mode but need no special permission.
 */
public final class ReminderScheduler {

    public static final String ACTION_REMINDER = "com.kuet.clubhub.action.REMINDER";
    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_VENUE = "venue";
    public static final String EXTRA_START = "start";
    public static final String EXTRA_KIND = "kind";

    public static final String KIND_DAY = "day";
    public static final String KIND_HOUR = "hour";
    public static final String KIND_TEST = "test";

    private static final long HOUR = 60 * 60 * 1000L;
    private static final long DAY = 24 * HOUR;

    private ReminderScheduler() {
    }

    /** Schedules (or cancels) both reminders for one stored event, depending on its status. */
    public static void scheduleFor(Context context, ScheduledEventEntity e) {
        if (!MyRegistration.REGISTERED.equals(e.status)
                || !NotificationPrefs.remindersEnabled(context)) {
            cancel(context, e.eventId);
            return;
        }
        schedule(context, e, KIND_DAY, e.startMillis - DAY);
        schedule(context, e, KIND_HOUR, e.startMillis - HOUR);
    }

    /** Re-creates every alarm from the stored events (after boot, app update or a settings change). */
    public static void rescheduleAll(Context context) {
        List<ScheduledEventEntity> rows =
                AppDatabase.getInstance(context).scheduleDao().getAll();
        for (ScheduledEventEntity row : rows) {
            scheduleFor(context, row);
        }
    }

    public static void cancel(Context context, String eventId) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) {
            return;
        }
        for (String kind : new String[]{KIND_DAY, KIND_HOUR}) {
            PendingIntent pi = PendingIntent.getBroadcast(context, requestCode(eventId, kind),
                    baseIntent(context), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }
    }

    /** Fires a sample reminder after the delay, to show the alarm -> receiver -> notification path. */
    public static void scheduleTest(Context context, long delayMillis) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) {
            return;
        }
        Intent intent = baseIntent(context);
        intent.putExtra(EXTRA_KIND, KIND_TEST);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode("test", KIND_TEST), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayMillis, pi);
    }

    private static void schedule(Context context, ScheduledEventEntity e, String kind, long triggerAt) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) {
            return;
        }
        Intent intent = baseIntent(context);
        intent.putExtra(EXTRA_EVENT_ID, e.eventId);
        intent.putExtra(EXTRA_TITLE, e.title);
        intent.putExtra(EXTRA_VENUE, e.venue);
        intent.putExtra(EXTRA_START, e.startMillis);
        intent.putExtra(EXTRA_KIND, kind);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode(e.eventId, kind), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (triggerAt <= System.currentTimeMillis()) {
            am.cancel(pi); // that moment has passed
            return;
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
    }

    private static Intent baseIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        return intent;
    }

    private static int requestCode(String eventId, String kind) {
        return (eventId + "|" + kind).hashCode();
    }
}

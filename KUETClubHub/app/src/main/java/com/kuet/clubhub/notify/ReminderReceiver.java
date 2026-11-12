package com.kuet.clubhub.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.kuet.clubhub.R;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.util.Io;
import com.kuet.clubhub.util.NotificationPrefs;
import com.kuet.clubhub.util.TimeFormats;

import java.util.Date;

/** Woken by a reminder alarm; posts a notification and toast that opens the event. */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ReminderScheduler.ACTION_REMINDER.equals(intent.getAction())) {
            return;
        }
        final Context app = context.getApplicationContext();
        final String kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND);
        final String eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID);
        final String title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE);
        final String venue = intent.getStringExtra(ReminderScheduler.EXTRA_VENUE);
        final long start = intent.getLongExtra(ReminderScheduler.EXTRA_START, 0);

        final PendingResult pending = goAsync();
        Io.DISK.execute(() -> {
            try {
                handle(app, kind, eventId, title, venue, start);
            } finally {
                pending.finish();
            }
        });
    }

    private void handle(Context app, String kind, String eventId, String title, String venue,
                        long start) {
        if (ReminderScheduler.KIND_TEST.equals(kind)) {
            NotificationHelper.show(app, NotificationHelper.CHANNEL_REMINDERS, 2001,
                    app.getString(R.string.test_reminder_title),
                    app.getString(R.string.test_reminder_text),
                    NotificationHelper.alertsPendingIntent(app, 2001));
            ToastCenter.show(app, app.getString(R.string.test_reminder_title) + ": "
                    + app.getString(R.string.test_reminder_text));
            return;
        }
        if (eventId == null || title == null || !NotificationPrefs.remindersEnabled(app)) {
            return;
        }
        // The person may have cancelled since the alarm was set.
        int stillRegistered = AppDatabase.getInstance(app).scheduleDao().countRegistered(eventId);
        if (stillRegistered == 0) {
            return;
        }

        if (ReminderScheduler.KIND_START.equals(kind)) {
            ToastCenter.eventStartingOrCheckInOpen(app, title, false, false, eventId);
            return;
        }

        String time = TimeFormats.time(new Date(start));
        String where = venue == null || venue.isEmpty() ? "" : "  •  " + venue;
        String heading = ReminderScheduler.KIND_DAY.equals(kind)
                ? app.getString(R.string.reminder_day_title, title)
                : app.getString(R.string.reminder_hour_title, title);
        String text = ReminderScheduler.KIND_DAY.equals(kind)
                ? app.getString(R.string.reminder_day_text, time) + where
                : app.getString(R.string.reminder_hour_text, time) + where;

        int id = (eventId + kind).hashCode();
        NotificationHelper.show(app, NotificationHelper.CHANNEL_REMINDERS, id, heading, text,
                NotificationHelper.eventPendingIntent(app, eventId, id));
        ToastCenter.show(app, heading + " — " + text);
    }
}

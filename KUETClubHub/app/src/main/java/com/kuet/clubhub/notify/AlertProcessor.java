package com.kuet.clubhub.notify;

import android.content.Context;
import android.content.Intent;

import com.google.firebase.firestore.DocumentSnapshot;
import com.kuet.clubhub.R;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.local.AlertEntity;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.Interests;
import com.kuet.clubhub.util.Io;
import com.kuet.clubhub.util.NotificationPrefs;
import com.kuet.clubhub.util.TimeFormats;

import java.util.Date;
import java.util.List;

/**
 * Decides whether a newly posted event deserves an alert for the signed-in student: it must be
 * new, upcoming, not their own, and carry a tag that matches one of their interests. The alerts
 * table has one row per event, so the foreground service and the background worker can both run
 * this without the student ever being told twice.
 */
public final class AlertProcessor {

    /** Sent inside the app when a new alert is stored (extra "title"). */
    public static final String ACTION_ALERTS_CHANGED = "com.kuet.clubhub.action.ALERTS_CHANGED";
    public static final String EXTRA_TITLE = "title";

    private AlertProcessor() {
    }

    /** Called from a Firestore listener (main thread): parse here, work on the disk thread. */
    public static void processDocument(Context context, DocumentSnapshot doc) {
        final Event event = doc.toObject(Event.class);
        final Date created = doc.getDate("createdAt");
        if (event == null || created == null) {
            return; // a just-written local event has no server time yet
        }
        event.setId(doc.getId());
        final Context app = context.getApplicationContext();
        Io.DISK.execute(() -> handle(app, event, created.getTime()));
    }

    /** Must run off the main thread. */
    public static void handle(Context app, Event event, long createdMillis) {
        if (!NotificationPrefs.alertsEnabled(app)
                || createdMillis <= NotificationPrefs.baseline(app)) {
            return;
        }
        UserProfile me = new SessionManager(app).getProfile();
        if (me == null || me.getUid().equals(event.getCreatedBy())) {
            return;
        }
        Date start = event.getStartTime();
        if (start == null || start.getTime() < System.currentTimeMillis()) {
            return;
        }
        List<String> matched = Interests.matched(me.getInterests(), event.getTags());
        if (matched.isEmpty()) {
            return;
        }

        String when = TimeFormats.dateTime(start);
        String club = event.getClubName() == null ? "" : event.getClubName();
        String tags = "#" + android.text.TextUtils.join(" #", matched);
        String message = app.getString(R.string.alert_message, club, when, tags);

        AlertEntity alert = AlertEntity.create(event.getId(), event.getTitle(), message);
        long row = AppDatabase.getInstance(app).alertDao().insertIgnore(alert);
        if (row == -1) {
            return; // already alerted for this event
        }
        int id = ("alert" + event.getId()).hashCode();
        NotificationHelper.show(app, NotificationHelper.CHANNEL_ALERTS, id,
                app.getString(R.string.alert_title, event.getTitle()), message,
                NotificationHelper.eventPendingIntent(app, event.getId(), id));
        announce(app, event.getTitle());
    }

    /** Stores and shows a made-up alert, so the notification path can be tried without a 2nd phone. */
    public static void postTestAlert(Context context) {
        final Context app = context.getApplicationContext();
        Io.DISK.execute(() -> {
            String id = "test-" + System.currentTimeMillis();
            String title = app.getString(R.string.test_alert_event);
            String message = app.getString(R.string.test_alert_message);
            AppDatabase.getInstance(app).alertDao().insertIgnore(AlertEntity.create(id, title, message));
            NotificationHelper.show(app, NotificationHelper.CHANNEL_ALERTS, 2002,
                    app.getString(R.string.alert_title, title), message,
                    NotificationHelper.alertsPendingIntent(app, 2002));
            announce(app, title);
        });
    }

    private static void announce(Context app, String title) {
        Intent intent = new Intent(ACTION_ALERTS_CHANGED);
        intent.setPackage(app.getPackageName());
        intent.putExtra(EXTRA_TITLE, title);
        app.sendBroadcast(intent);
    }
}

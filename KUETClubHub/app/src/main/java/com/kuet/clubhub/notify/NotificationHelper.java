package com.kuet.clubhub.notify;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.format.DateFormat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.kuet.clubhub.R;
import com.kuet.clubhub.ui.MainActivity;
import com.kuet.clubhub.ui.alerts.AlertsActivity;
import com.kuet.clubhub.ui.clubs.ClubChatActivity;
import com.kuet.clubhub.ui.clubs.ClubDetailActivity;
import com.kuet.clubhub.ui.events.EventDetailActivity;

import java.util.Date;

/** Notification channels and the notifications the app posts. */
public final class NotificationHelper {

    public static final String CHANNEL_REMINDERS = "reminders";
    public static final String CHANNEL_ALERTS = "interest_alerts";
    public static final String CHANNEL_WATCHER = "watcher";

    public static final int WATCHER_NOTIFICATION_ID = 1001;
    private static final int WATCHER_ACTION_ALERTS_CODE = 1003;
    private static final int WATCHER_ACTION_OFF_CODE = 1004;

    private NotificationHelper() {
    }

    public static void createChannels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel reminders = new NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH);
        reminders.setDescription(context.getString(R.string.channel_reminders_desc));

        NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT);
        alerts.setDescription(context.getString(R.string.channel_alerts_desc));

        NotificationChannel watcher = new NotificationChannel(CHANNEL_WATCHER,
                context.getString(R.string.channel_watcher), NotificationManager.IMPORTANCE_LOW);
        watcher.setDescription(context.getString(R.string.channel_watcher_desc));

        nm.createNotificationChannel(reminders);
        nm.createNotificationChannel(alerts);
        nm.createNotificationChannel(watcher);
    }

    /** True when the system lets this app show notifications right now. */
    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** Opens the event page, with the main screen underneath so Back leads into the app. */
    public static PendingIntent eventPendingIntent(Context context, String eventId, int requestCode) {
        Intent detail = new Intent(context, EventDetailActivity.class);
        detail.putExtra(EventDetailActivity.EXTRA_EVENT_ID, eventId);
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(detail)
                .getPendingIntent(requestCode,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Opens the club detail page, with the main screen underneath. */
    public static PendingIntent clubPendingIntent(Context context, String clubId, int requestCode) {
        Intent detail = new Intent(context, ClubDetailActivity.class);
        detail.putExtra(ClubDetailActivity.EXTRA_CLUB_ID, clubId);
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(detail)
                .getPendingIntent(requestCode,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Opens the club group chat screen, with the main screen and club page underneath. */
    public static PendingIntent clubChatPendingIntent(Context context, @Nullable String clubId,
                                                      @Nullable String clubName, int requestCode) {
        if (clubId == null || clubId.isEmpty()) {
            return alertsPendingIntent(context, requestCode);
        }
        Intent clubDetail = new Intent(context, ClubDetailActivity.class);
        clubDetail.putExtra(ClubDetailActivity.EXTRA_CLUB_ID, clubId);
        Intent chat = new Intent(context, ClubChatActivity.class);
        chat.putExtra(ClubChatActivity.EXTRA_CLUB_ID, clubId);
        if (clubName != null) {
            chat.putExtra(ClubChatActivity.EXTRA_CLUB_NAME, clubName);
        }
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(clubDetail)
                .addNextIntent(chat)
                .getPendingIntent(requestCode,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent alertsPendingIntent(Context context, int requestCode) {
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(new Intent(context, AlertsActivity.class))
                .getPendingIntent(requestCode,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void show(Context context, String channel, int id, String title, String text,
                            @Nullable PendingIntent contentIntent) {
        if (!canNotify(context)) {
            return;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_clubhub)
                .setColor(ContextCompat.getColor(context, R.color.kuet_green))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(CHANNEL_REMINDERS.equals(channel)
                        ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT);
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent);
        }
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    /**
     * The always-visible notification of the foreground service that watches for matching events.
     * It shows live status (connecting / live / offline, matches found so far, last check time),
     * a running "watching for" timer, and two actions. It is re-posted by {@link #updateWatcher}.
     */
    public static Notification buildWatcherNotification(Context context) {
        WatcherStatus.Snapshot s = WatcherStatus.snapshot();
        String checked = s.lastCheckedAt == 0 ? null
                : DateFormat.getTimeFormat(context).format(new Date(s.lastCheckedAt));

        String text;
        if (s.state == WatcherStatus.State.OFFLINE) {
            text = context.getString(R.string.watcher_state_offline);
        } else if (s.state == WatcherStatus.State.CONNECTING) {
            text = context.getString(R.string.watcher_state_connecting);
        } else if (s.matches > 0) {
            text = context.getResources().getQuantityString(R.plurals.watcher_state_matches,
                    s.matches, s.matches, s.lastTitle == null ? "" : s.lastTitle);
        } else {
            text = context.getString(R.string.watcher_state_idle);
        }
        String detail = checked == null ? text
                : text + "\n" + context.getString(R.string.watcher_last_checked, checked);

        return new NotificationCompat.Builder(context, CHANNEL_WATCHER)
                .setSmallIcon(R.drawable.ic_stat_clubhub)
                .setColor(ContextCompat.getColor(context, R.color.kuet_green))
                .setContentTitle(context.getString(R.string.watcher_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(detail))
                .setWhen(s.startedAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(0, context.getString(R.string.watcher_action_alerts),
                        alertsPendingIntent(context, WATCHER_ACTION_ALERTS_CODE))
                .addAction(0, context.getString(R.string.watcher_action_off),
                        PendingIntent.getService(context, WATCHER_ACTION_OFF_CODE,
                                new Intent(context, InterestAlertService.class)
                                        .setAction(InterestAlertService.ACTION_TURN_OFF),
                                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
    }

    /** Re-posts the foreground notification with the current {@link WatcherStatus}. */
    public static void updateWatcher(Context context) {
        if (!canNotify(context)) {
            return;
        }
        NotificationManagerCompat.from(context)
                .notify(WATCHER_NOTIFICATION_ID, buildWatcherNotification(context));
    }
}

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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.kuet.clubhub.R;
import com.kuet.clubhub.ui.MainActivity;
import com.kuet.clubhub.ui.alerts.AlertsActivity;
import com.kuet.clubhub.ui.events.EventDetailActivity;

/** Notification channels and the notifications the app posts. */
public final class NotificationHelper {

    public static final String CHANNEL_REMINDERS = "reminders";
    public static final String CHANNEL_ALERTS = "interest_alerts";
    public static final String CHANNEL_WATCHER = "watcher";

    public static final int WATCHER_NOTIFICATION_ID = 1001;

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

    /** The always-visible notification of the foreground service that watches for matching events. */
    public static Notification buildWatcherNotification(Context context) {
        return new NotificationCompat.Builder(context, CHANNEL_WATCHER)
                .setSmallIcon(R.drawable.ic_stat_clubhub)
                .setColor(ContextCompat.getColor(context, R.color.kuet_green))
                .setContentTitle(context.getString(R.string.watcher_title))
                .setContentText(context.getString(R.string.watcher_text))
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
    }
}

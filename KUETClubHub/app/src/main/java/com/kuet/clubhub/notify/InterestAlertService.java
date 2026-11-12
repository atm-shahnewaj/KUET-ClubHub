package com.kuet.clubhub.notify;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.kuet.clubhub.util.NotificationPrefs;

/**
 * Foreground service that keeps a live Firestore listener on the events collection while the
 * student has interest alerts switched on, and also keeps {@link RealtimeToastWatcher} active for
 * background/offline-sync club chat, waitlist promotion, and event update notifications.
 */
public class InterestAlertService extends Service {

    /** Sent by the "Turn off" button on the ongoing notification. */
    public static final String ACTION_TURN_OFF = "com.kuet.clubhub.action.WATCHER_TURN_OFF";

    private ListenerRegistration registration;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TURN_OFF.equals(intent.getAction())) {
            NotificationPrefs.setAlertsEnabled(this, false);
            AlertController.stopAll(this);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        WatcherStatus.onServiceStarted();
        // A service started with startForegroundService() must show its notification promptly.
        ServiceCompat.startForeground(this, NotificationHelper.WATCHER_NOTIFICATION_ID,
                NotificationHelper.buildWatcherNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        if (!AlertController.shouldRun(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        RealtimeToastWatcher.getInstance(this).ensureWatching();
        listen();
        return START_STICKY;
    }

    private void listen() {
        if (registration != null) {
            return;
        }
        // MetadataChanges.INCLUDE also delivers the "now confirmed by the server" update, even
        // when the data itself did not change; that is what flips the notification to "Live".
        registration = FirebaseFirestore.getInstance().collection("events")
                .addSnapshotListener(MetadataChanges.INCLUDE, (snap, error) -> {
                    if (error != null || snap == null) {
                        WatcherStatus.onListenerResult(this, true, false);
                        return;
                    }
                    WatcherStatus.onListenerResult(this, false,
                            snap.getMetadata().isFromCache());
                    for (DocumentChange change : snap.getDocumentChanges()) {
                        if (change.getType() == DocumentChange.Type.ADDED) {
                            AlertProcessor.processDocument(this, change.getDocument());
                        }
                    }
                });
    }

    @Override
    public void onDestroy() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        WatcherStatus.onServiceStopped();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

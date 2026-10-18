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

/**
 * Foreground service that keeps a live Firestore listener on the events collection while the
 * student has interest alerts switched on. Each newly appearing event goes through
 * {@link AlertProcessor}.
 */
public class InterestAlertService extends Service {

    private ListenerRegistration registration;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A service started with startForegroundService() must show its notification promptly.
        ServiceCompat.startForeground(this, NotificationHelper.WATCHER_NOTIFICATION_ID,
                NotificationHelper.buildWatcherNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        if (!AlertController.shouldRun(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        listen();
        return START_STICKY;
    }

    private void listen() {
        if (registration != null) {
            return;
        }
        registration = FirebaseFirestore.getInstance().collection("events")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        return;
                    }
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
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

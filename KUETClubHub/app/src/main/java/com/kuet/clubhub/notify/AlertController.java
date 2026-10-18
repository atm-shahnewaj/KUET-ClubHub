package com.kuet.clubhub.notify;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.NotificationPrefs;

import java.util.concurrent.TimeUnit;

/** Starts and stops the interest-alert foreground service and its WorkManager backup. */
public final class AlertController {

    private static final String WORK_NAME = "interest_check";

    private AlertController() {
    }

    /** True when somebody is signed in, alerts are on, and they picked at least one interest. */
    public static boolean shouldRun(Context context) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null
                || !NotificationPrefs.alertsEnabled(context)) {
            return false;
        }
        UserProfile profile = new SessionManager(context).getProfile();
        return profile != null && profile.getInterests() != null
                && !profile.getInterests().isEmpty();
    }

    /** Call from the UI (the app is visible, so a foreground service may start). */
    public static void refresh(Context context) {
        if (shouldRun(context)) {
            NotificationPrefs.ensureBaseline(context);
            startService(context);
            enqueueWorker(context);
        } else {
            stopAll(context);
        }
    }

    /** Called after a reboot or app update. Starting the service may be refused; that is fine. */
    public static void restartBackground(Context context) {
        if (shouldRun(context)) {
            enqueueWorker(context);
            startService(context);
        }
    }

    public static void stopAll(Context context) {
        context.stopService(new Intent(context, InterestAlertService.class));
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    private static void startService(Context context) {
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, InterestAlertService.class));
        } catch (RuntimeException e) {
            Log.w("AlertController", "Could not start the alert service now", e);
        }
    }

    private static void enqueueWorker(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                InterestCheckWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}

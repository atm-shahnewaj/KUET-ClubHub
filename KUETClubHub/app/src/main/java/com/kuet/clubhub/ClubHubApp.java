package com.kuet.clubhub;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;
import com.kuet.clubhub.notify.NotificationHelper;
import com.kuet.clubhub.util.ThemeManager;

public class ClubHubApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception ignored) {
        }
        ThemeManager.applySaved(this);
        NotificationHelper.createChannels(this);
    }
}

package com.kuet.clubhub.notify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.util.NotificationPrefs;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Backup for the foreground service: every half hour (when online) it asks Firestore for events
 * created since the alert baseline. Covers phones that stop the service to save battery.
 */
public class InterestCheckWorker extends Worker {

    public InterestCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context app = getApplicationContext();
        if (!AlertController.shouldRun(app)) {
            return Result.success();
        }
        try {
            QuerySnapshot snap = Tasks.await(FirebaseFirestore.getInstance().collection("events")
                    .whereGreaterThan("createdAt", new Date(NotificationPrefs.baseline(app)))
                    .get(), 30, TimeUnit.SECONDS);
            for (DocumentSnapshot doc : snap.getDocuments()) {
                Event event = doc.toObject(Event.class);
                Date created = doc.getDate("createdAt");
                if (event != null && created != null) {
                    event.setId(doc.getId());
                    AlertProcessor.handle(app, event, created.getTime());
                }
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}

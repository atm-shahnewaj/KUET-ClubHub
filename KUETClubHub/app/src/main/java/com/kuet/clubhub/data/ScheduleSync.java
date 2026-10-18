package com.kuet.clubhub.data;

import android.content.Context;

import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.data.local.ScheduleDao;
import com.kuet.clubhub.data.local.ScheduledEventEntity;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.notify.ReminderScheduler;
import com.kuet.clubhub.util.Io;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mirrors the student's registrations into Room (served by the ClubHubProvider) and keeps the
 * reminder alarms in step with them.
 */
public final class ScheduleSync {

    private ScheduleSync() {
    }

    /** Called whenever the live list of my registrations changes. */
    public static void sync(Context context, List<MyRegistration> registrations) {
        final Context app = context.getApplicationContext();
        final long now = System.currentTimeMillis();

        final List<ScheduledEventEntity> rows = new ArrayList<>();
        for (MyRegistration r : registrations) {
            if (!r.isActive() || r.getStartTime() == null) {
                continue;
            }
            ScheduledEventEntity row = ScheduledEventEntity.fromRegistration(r);
            if (row.endMillis >= now) { // finished events are of no use
                rows.add(row);
            }
        }

        Io.DISK.execute(() -> {
            ScheduleDao dao = AppDatabase.getInstance(app).scheduleDao();
            List<ScheduledEventEntity> before = dao.getAll();
            dao.replaceAll(rows);

            Set<String> keep = new HashSet<>();
            for (ScheduledEventEntity row : rows) {
                keep.add(row.eventId);
                ReminderScheduler.scheduleFor(app, row);
            }
            for (ScheduledEventEntity old : before) {
                if (!keep.contains(old.eventId)) {
                    ReminderScheduler.cancel(app, old.eventId);
                }
            }
        });
    }

    /** Called on logout: forget the schedule and every alarm and alert. */
    public static void clearAll(Context context) {
        final Context app = context.getApplicationContext();
        Io.DISK.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(app);
            for (ScheduledEventEntity old : db.scheduleDao().getAll()) {
                ReminderScheduler.cancel(app, old.eventId);
            }
            db.scheduleDao().deleteAll();
            db.alertDao().deleteAll();
        });
    }
}

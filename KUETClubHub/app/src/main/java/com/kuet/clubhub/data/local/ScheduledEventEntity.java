package com.kuet.clubhub.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.kuet.clubhub.model.MyRegistration;

/**
 * The signed-in student's registered / waitlisted events, kept on the device. Reminder alarms are
 * rebuilt from these rows after a reboot, and the ClubHubProvider serves them to the clash check.
 */
@Entity(tableName = "registered_events")
public class ScheduledEventEntity {

    @PrimaryKey
    @NonNull
    public String eventId = "";

    public String title;
    public String venue;
    public long startMillis;
    public long endMillis;
    public String status;
    public String passCode;

    public static ScheduledEventEntity fromRegistration(MyRegistration r) {
        ScheduledEventEntity e = new ScheduledEventEntity();
        e.eventId = r.getEventId() == null ? "" : r.getEventId();
        e.title = r.getTitle();
        e.venue = r.getVenue();
        e.startMillis = r.getStartTime() == null ? 0 : r.getStartTime().getTime();
        e.endMillis = r.getEndTime() == null ? e.startMillis : r.getEndTime().getTime();
        e.status = r.getStatus();
        e.passCode = r.getPassCode();
        return e;
    }
}

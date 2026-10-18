package com.kuet.clubhub.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One in-app alert: "a new event matches your interests". One row per event, so never twice. */
@Entity(tableName = "alerts")
public class AlertEntity {

    @PrimaryKey
    @NonNull
    public String eventId = "";

    public String title;
    public String message;
    public long createdMillis;
    public boolean seen;
    public boolean dismissed;

    public static AlertEntity create(String eventId, String title, String message) {
        AlertEntity a = new AlertEntity();
        a.eventId = eventId;
        a.title = title;
        a.message = message;
        a.createdMillis = System.currentTimeMillis();
        return a;
    }
}

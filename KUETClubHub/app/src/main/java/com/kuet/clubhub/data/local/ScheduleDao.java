package com.kuet.clubhub.data.local;

import android.database.Cursor;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class ScheduleDao {

    @Query("SELECT * FROM registered_events")
    public abstract List<ScheduledEventEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertAll(List<ScheduledEventEntity> rows);

    @Query("DELETE FROM registered_events")
    public abstract void deleteAll();

    @Transaction
    public void replaceAll(List<ScheduledEventEntity> rows) {
        deleteAll();
        insertAll(rows);
    }

    @Query("SELECT COUNT(*) FROM registered_events WHERE eventId = :eventId AND status = 'registered'")
    public abstract int countRegistered(String eventId);

    @Query("SELECT * FROM registered_events WHERE eventId = :eventId LIMIT 1")
    public abstract ScheduledEventEntity findById(String eventId);

    /** Raw cursors for the ContentProvider. */
    @Query("SELECT * FROM registered_events WHERE status = 'registered' ORDER BY startMillis ASC")
    public abstract Cursor registeredCursor();

    @Query("SELECT * FROM registered_events WHERE status = 'registered' AND eventId = :eventId")
    public abstract Cursor registeredCursorById(String eventId);
}

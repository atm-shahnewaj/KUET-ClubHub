package com.kuet.clubhub.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AlertDao {

    /** Returns the new row id, or -1 if an alert for this event already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(AlertEntity alert);

    @Query("SELECT * FROM alerts WHERE dismissed = 0 ORDER BY createdMillis DESC")
    LiveData<List<AlertEntity>> observeVisible();

    @Query("SELECT COUNT(*) FROM alerts WHERE seen = 0 AND dismissed = 0")
    LiveData<Integer> observeUnreadCount();

    @Query("UPDATE alerts SET seen = 1 WHERE seen = 0")
    void markAllSeen();

    /** Hides every alert but keeps the rows, so the same event is never alerted again. */
    @Query("UPDATE alerts SET dismissed = 1")
    void dismissAll();

    @Query("DELETE FROM alerts")
    void deleteAll();
}

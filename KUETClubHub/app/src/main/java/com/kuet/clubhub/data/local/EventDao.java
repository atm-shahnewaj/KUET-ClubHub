package com.kuet.clubhub.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class EventDao {

    @Query("SELECT * FROM events ORDER BY startMillis ASC")
    public abstract LiveData<List<EventEntity>> observeAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertAll(List<EventEntity> events);

    @Query("DELETE FROM events")
    public abstract void deleteAll();

    /** Swaps the whole cache for a fresh server snapshot in one transaction. */
    @Transaction
    public void replaceAll(List<EventEntity> events) {
        deleteAll();
        insertAll(events);
    }
}

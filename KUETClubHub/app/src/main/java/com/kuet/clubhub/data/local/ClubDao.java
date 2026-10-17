package com.kuet.clubhub.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class ClubDao {

    @Query("SELECT * FROM clubs ORDER BY name COLLATE NOCASE ASC")
    public abstract LiveData<List<ClubEntity>> observeAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertAll(List<ClubEntity> clubs);

    @Query("DELETE FROM clubs")
    public abstract void deleteAll();

    /** Swaps the whole cache for a fresh server snapshot in one transaction. */
    @Transaction
    public void replaceAll(List<ClubEntity> clubs) {
        deleteAll();
        insertAll(clubs);
    }
}

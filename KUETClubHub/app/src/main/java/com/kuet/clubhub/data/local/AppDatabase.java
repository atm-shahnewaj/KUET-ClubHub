package com.kuet.clubhub.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ClubEntity.class, EventEntity.class, ScheduledEventEntity.class,
        AlertEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract ClubDao clubDao();

    public abstract EventDao eventDao();

    public abstract ScheduleDao scheduleDao();

    public abstract AlertDao alertDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "clubhub.db")
                            // It is only a cache, so wiping it on a schema change is fine.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}

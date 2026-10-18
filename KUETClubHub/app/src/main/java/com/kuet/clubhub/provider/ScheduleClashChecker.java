package com.kuet.clubhub.provider;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.util.Io;

/**
 * Finds out whether an event overlaps one the student is already registered for, by reading the
 * ClubHubProvider through a ContentResolver (on a background thread; the result comes back on the
 * main thread).
 */
public final class ScheduleClashChecker {

    public static final class Clash {
        public final String title;
        public final long startMillis;
        public final long endMillis;

        Clash(String title, long startMillis, long endMillis) {
            this.title = title;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }
    }

    public interface Listener {
        /** clash is null when the event fits the student's schedule. */
        void onResult(@Nullable Clash clash);
    }

    private static final long ONE_HOUR = 60 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ScheduleClashChecker() {
    }

    public static void check(Context context, Event candidate, Listener listener) {
        final Context app = context.getApplicationContext();
        final String candidateId = candidate.getId();
        final long start = candidate.getStartTime() == null ? 0 : candidate.getStartTime().getTime();
        final long end = candidate.getEndTime() == null ? start + ONE_HOUR
                : candidate.getEndTime().getTime();

        Io.DISK.execute(() -> {
            Clash found = null;
            try (Cursor cursor = app.getContentResolver()
                    .query(ClubHubProvider.REGISTERED_EVENTS_URI, null, null, null, null)) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndexOrThrow(ClubHubProvider.COL_EVENT_ID);
                    int titleCol = cursor.getColumnIndexOrThrow(ClubHubProvider.COL_TITLE);
                    int startCol = cursor.getColumnIndexOrThrow(ClubHubProvider.COL_START);
                    int endCol = cursor.getColumnIndexOrThrow(ClubHubProvider.COL_END);
                    while (cursor.moveToNext()) {
                        if (cursor.getString(idCol).equals(candidateId)) {
                            continue; // the same event
                        }
                        long otherStart = cursor.getLong(startCol);
                        long otherEnd = Math.max(cursor.getLong(endCol), otherStart + 1);
                        if (start < otherEnd && otherStart < end) {
                            found = new Clash(cursor.getString(titleCol), otherStart, otherEnd);
                            break;
                        }
                    }
                }
            } catch (RuntimeException e) {
                found = null; // if the check fails, do not block registration
            }
            final Clash result = found;
            MAIN.post(() -> listener.onResult(result));
        });
    }
}

package com.kuet.clubhub.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.data.local.ScheduleDao;

/**
 * Read-only content provider that shares the signed-in student's registered events (id, title,
 * venue, start and end time). The schedule-clash check reads it through a ContentResolver.
 *
 *   content://com.kuet.clubhub.provider/registered_events        all registered events
 *   content://com.kuet.clubhub.provider/registered_events/{id}   one event
 *
 * The manifest guards it with a signature-level read permission and a write permission nobody
 * holds, so other apps cannot see the schedule and nobody can change it through the provider.
 */
public class ClubHubProvider extends ContentProvider {

    public static final String AUTHORITY = "com.kuet.clubhub.provider";
    public static final Uri REGISTERED_EVENTS_URI =
            Uri.parse("content://" + AUTHORITY + "/registered_events");

    public static final String COL_EVENT_ID = "eventId";
    public static final String COL_TITLE = "title";
    public static final String COL_VENUE = "venue";
    public static final String COL_START = "startMillis";
    public static final String COL_END = "endMillis";

    private static final int REGISTERED_EVENTS = 1;
    private static final int REGISTERED_EVENT_ID = 2;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "registered_events", REGISTERED_EVENTS);
        MATCHER.addURI(AUTHORITY, "registered_events/*", REGISTERED_EVENT_ID);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Projection, selection and sort order are ignored: the two queries below are fixed. */
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (getContext() == null) {
            return null;
        }
        ScheduleDao dao = AppDatabase.getInstance(getContext()).scheduleDao();
        switch (MATCHER.match(uri)) {
            case REGISTERED_EVENTS:
                return dao.registeredCursor();
            case REGISTERED_EVENT_ID:
                return dao.registeredCursorById(uri.getLastPathSegment());
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case REGISTERED_EVENTS:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".registered_event";
            case REGISTERED_EVENT_ID:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".registered_event";
            default:
                return null;
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("This provider is read-only");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("This provider is read-only");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("This provider is read-only");
    }
}

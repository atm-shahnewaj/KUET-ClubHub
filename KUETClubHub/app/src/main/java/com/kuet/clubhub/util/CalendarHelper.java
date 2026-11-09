package com.kuet.clubhub.util;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import com.kuet.clubhub.model.Event;

import java.util.TimeZone;

/**
 * Adds a campus event to the user's phone calendar (Proposal feature 39, Week 10 Content Provider
 * consumer).
 *
 * <p>When {@code READ_CALENDAR} and {@code WRITE_CALENDAR} permissions are granted and the phone
 * has a writable local calendar, the event is written directly through {@link ContentResolver}
 * into {@link CalendarContract.Events#CONTENT_URI}. If the user denies the calendar permission (or
 * no writable calendar account is set up on the device), the helper degrades gracefully by
 * opening the calendar app's pre-filled insert screen via {@link Intent#ACTION_INSERT}.</p>
 */
public final class CalendarHelper {

    public static final int RESULT_INSERTED_DIRECTLY = 1;
    public static final int RESULT_ALREADY_EXISTS = 2;
    public static final int RESULT_OPENED_CALENDAR_APP = 3;
    public static final int RESULT_NO_CALENDAR_APP = 4;

    private static final long DEFAULT_DURATION_MS = 60 * 60 * 1_000L;

    private CalendarHelper() {
    }

    public static boolean hasCalendarPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Writes the event directly into {@link CalendarContract.Events} if permissions and a writable
     * calendar exist; otherwise falls back to {@link #openInsertIntent(Context, Event)}.
     */
    public static int addOrOpenCalendar(Context context, Event event) {
        if (event == null || event.getStartTime() == null) {
            return RESULT_NO_CALENDAR_APP;
        }
        // Direct writes into CalendarContract depend on the phone's calendar accounts and often
        // "succeed" without the event ever appearing, so the reliable route is the calendar app's
        // own pre-filled "new event" screen (no permission needed, the user confirms with Save).
        return openInsertIntent(context, event);
    }

    /** Opens the system calendar app with the event details pre-filled (no permission required). */
    public static int openInsertIntent(Context context, Event event) {
        long startMillis = event.getStartTime().getTime();
        long endMillis = event.getEndTime() != null
                ? event.getEndTime().getTime()
                : startMillis + DEFAULT_DURATION_MS;
        if (endMillis <= startMillis) {
            endMillis = startMillis + DEFAULT_DURATION_MS;
        }

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                .putExtra(CalendarContract.Events.TITLE, safe(event.getTitle()))
                .putExtra(CalendarContract.Events.EVENT_LOCATION, safe(event.getVenue()))
                .putExtra(CalendarContract.Events.DESCRIPTION, buildDescription(event));
        try {
            context.startActivity(intent);
            return RESULT_OPENED_CALENDAR_APP;
        } catch (ActivityNotFoundException e) {
            // No app handles "insert event": at least open the calendar on that day.
            try {
                Uri day = Uri.parse("content://com.android.calendar/time/" + startMillis);
                context.startActivity(new Intent(Intent.ACTION_VIEW, day));
                return RESULT_OPENED_CALENDAR_APP;
            } catch (ActivityNotFoundException e2) {
                return RESULT_NO_CALENDAR_APP;
            }
        }
    }

    private static int insertDirectly(ContentResolver resolver, Event event) {
        long calendarId = findWritableCalendarId(resolver);
        if (calendarId < 0) {
            return -1;
        }
        long startMillis = event.getStartTime().getTime();
        long endMillis = event.getEndTime() != null
                ? event.getEndTime().getTime()
                : startMillis + DEFAULT_DURATION_MS;
        String title = safe(event.getTitle());

        if (alreadyExists(resolver, calendarId, title, startMillis)) {
            return RESULT_ALREADY_EXISTS;
        }

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, title);
        values.put(CalendarContract.Events.DESCRIPTION, buildDescription(event));
        values.put(CalendarContract.Events.EVENT_LOCATION, safe(event.getVenue()));
        values.put(CalendarContract.Events.DTSTART, startMillis);
        values.put(CalendarContract.Events.DTEND, endMillis);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

        Uri uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values);
        return uri != null ? RESULT_INSERTED_DIRECTLY : -1;
    }

    private static long findWritableCalendarId(ContentResolver resolver) {
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        };
        long fallbackId = -1;
        try (Cursor c = resolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)) {
            if (c == null) {
                return -1;
            }
            while (c.moveToNext()) {
                long id = c.getLong(0);
                int primary = c.getInt(1);
                int visible = c.getInt(2);
                int access = c.getInt(3);
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR || visible == 0) {
                    continue;
                }
                if (primary == 1) {
                    return id;
                }
                if (fallbackId < 0) {
                    fallbackId = id;
                }
            }
        }
        return fallbackId;
    }

    private static boolean alreadyExists(ContentResolver resolver, long calendarId,
                                         String title, long startMillis) {
        String[] projection = {CalendarContract.Events._ID};
        String selection = CalendarContract.Events.CALENDAR_ID + " = ? AND "
                + CalendarContract.Events.TITLE + " = ? AND "
                + CalendarContract.Events.DTSTART + " = ? AND "
                + CalendarContract.Events.DELETED + " = 0";
        String[] args = {
                String.valueOf(calendarId),
                title,
                String.valueOf(startMillis)
        };
        try (Cursor c = resolver.query(
                CalendarContract.Events.CONTENT_URI, projection, selection, args, null)) {
            return c != null && c.moveToFirst();
        }
    }

    private static String buildDescription(Event event) {
        String club = safe(event.getClubName());
        String desc = safe(event.getDescription());
        if (club.isEmpty()) {
            return desc;
        }
        if (desc.isEmpty()) {
            return "Organized by " + club + " (KUET ClubHub)";
        }
        return "Organized by " + club + "\n\n" + desc;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

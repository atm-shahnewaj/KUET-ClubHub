package com.kuet.clubhub.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Small helpers for showing dates and times the same way everywhere. */
public final class TimeFormats {

    private TimeFormats() {
    }

    public static String fullDate(Date d) {
        return d == null ? "-" : new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(d);
    }

    public static String time(Date d) {
        return d == null ? "-" : new SimpleDateFormat("h:mm a", Locale.getDefault()).format(d);
    }

    public static String timeRange(Date start, Date end) {
        if (start == null) {
            return "-";
        }
        return end == null ? time(start) : time(start) + " – " + time(end);
    }

    public static String dayNumber(Date d) {
        return d == null ? "" : new SimpleDateFormat("d", Locale.getDefault()).format(d);
    }

    public static String monthShort(Date d) {
        return d == null ? "" : new SimpleDateFormat("MMM", Locale.getDefault()).format(d);
    }

    public static String monthYear(Calendar c) {
        return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.getTime());
    }

    public static String dateTime(Date d) {
        return d == null ? "-" : fullDate(d) + ", " + time(d);
    }

    public static boolean sameDay(Date a, int year, int month, int day) {
        if (a == null) {
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(a);
        return c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
                && c.get(Calendar.DAY_OF_MONTH) == day;
    }
}

package com.kuet.clubhub.util;

import java.util.Date;
import java.util.Locale;

/**
 * Formats live countdown strings for the event detail screen and the home screen widget
 * (Proposal feature 17).
 */
public final class CountdownText {

    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private CountdownText() {
    }

    /**
     * Detailed countdown for the event page (ticks every second while visible):
     * <ul>
     *   <li>Before start: {@code "Starts in 2d 04h 15m 08s"} or {@code "Starts in 03h 12m 09s"}.</li>
     *   <li>During event: {@code "Happening now · 42m 18s left"}.</li>
     *   <li>After end: {@code "Event ended"}.</li>
     * </ul>
     */
    public static String formatDetail(Date start, Date end, long nowMillis) {
        if (start == null) {
            return "-";
        }
        long startMillis = start.getTime();
        long endMillis = end != null ? end.getTime() : startMillis;
        if (nowMillis < startMillis) {
            long diff = startMillis - nowMillis;
            return "Starts in " + formatDurationWithSeconds(diff);
        }
        if (nowMillis <= endMillis) {
            long left = Math.max(0L, endMillis - nowMillis);
            if (left == 0L) {
                return "Happening now";
            }
            return "Happening now · " + formatDurationWithSeconds(left) + " left";
        }
        return "Event ended";
    }

    /**
     * Compact countdown badge for the home screen widget (updated periodically and on registration
     * changes, so it avoids seconds):
     * <ul>
     *   <li>{@code "In 3d 5h"}, {@code "In 4h 20m"}, {@code "In 25m"}, {@code "Starting soon"}</li>
     *   <li>{@code "Live now"} while in progress</li>
     *   <li>{@code "Ended"} after the event</li>
     * </ul>
     */
    public static String formatWidget(long startMillis, long endMillis, long nowMillis) {
        long effectiveEnd = endMillis > 0 ? endMillis : startMillis;
        if (nowMillis < startMillis) {
            long diff = startMillis - nowMillis;
            long days = diff / DAY;
            long hours = (diff % DAY) / HOUR;
            long minutes = (diff % HOUR) / MINUTE;
            if (days > 0) {
                return String.format(Locale.ENGLISH, "In %dd %dh", days, hours);
            }
            if (hours > 0) {
                return String.format(Locale.ENGLISH, "In %dh %dm", hours, minutes);
            }
            if (minutes > 0) {
                return String.format(Locale.ENGLISH, "In %dm", minutes);
            }
            return "Starting soon";
        }
        if (nowMillis <= effectiveEnd) {
            return "Live now";
        }
        return "Ended";
    }

    static String formatDurationWithSeconds(long millis) {
        long totalSeconds = Math.max(0L, millis / SECOND);
        long days = totalSeconds / (24 * 3600);
        long hours = (totalSeconds % (24 * 3600)) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (days > 0) {
            return String.format(Locale.ENGLISH, "%dd %02dh %02dm %02ds",
                    days, hours, minutes, seconds);
        }
        if (hours > 0) {
            return String.format(Locale.ENGLISH, "%02dh %02dm %02ds",
                    hours, minutes, seconds);
        }
        return String.format(Locale.ENGLISH, "%02dm %02ds", minutes, seconds);
    }
}

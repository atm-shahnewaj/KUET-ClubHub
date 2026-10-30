package com.kuet.clubhub.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Date;

public class CountdownTextTest {

    @Test
    public void formatDetailShowsDaysHoursMinutesSecondsBeforeStart() {
        long now = 1_000_000L;
        long start = now + (2 * 86_400_000L) + (4 * 3_600_000L) + (15 * 60_000L) + 8_000L;
        long end = start + 3_600_000L;
        assertEquals("Starts in 2d 04h 15m 08s",
                CountdownText.formatDetail(new Date(start), new Date(end), now));
    }

    @Test
    public void formatDetailShowsHappeningNowDuringEventAndEndedAfter() {
        long start = 1_000_000L;
        long end = start + 45 * 60_000L;
        long during = start + 15 * 60_000L;
        assertEquals("Happening now · 30m 00s left",
                CountdownText.formatDetail(new Date(start), new Date(end), during));
        assertEquals("Event ended",
                CountdownText.formatDetail(new Date(start), new Date(end), end + 1_000L));
    }

    @Test
    public void formatWidgetUsesCompactBadges() {
        long now = 1_000_000L;
        long startInDays = now + 3 * 86_400_000L + 5 * 3_600_000L;
        assertEquals("In 3d 5h",
                CountdownText.formatWidget(startInDays, startInDays + 3_600_000L, now));

        long startInHours = now + 2 * 3_600_000L + 20 * 60_000L;
        assertEquals("In 2h 20m",
                CountdownText.formatWidget(startInHours, startInHours + 3_600_000L, now));

        assertEquals("Live now",
                CountdownText.formatWidget(now - 60_000L, now + 60_000L, now));
        assertEquals("Ended",
                CountdownText.formatWidget(now - 120_000L, now - 60_000L, now));
    }
}

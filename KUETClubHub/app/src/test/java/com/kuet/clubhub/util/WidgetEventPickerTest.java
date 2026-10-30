package com.kuet.clubhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.kuet.clubhub.data.local.ScheduledEventEntity;
import com.kuet.clubhub.model.MyRegistration;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class WidgetEventPickerTest {

    private static ScheduledEventEntity row(String id, String status, long start, long end) {
        ScheduledEventEntity e = new ScheduledEventEntity();
        e.eventId = id;
        e.title = "Event " + id;
        e.status = status;
        e.startMillis = start;
        e.endMillis = end;
        return e;
    }

    @Test
    public void pickNextChoosesSoonestUpcomingRegisteredEventAndSkipsWaitlistedOrEnded() {
        long now = 5_000L;
        ScheduledEventEntity ended = row("ended", MyRegistration.REGISTERED, 1_000L, 4_000L);
        ScheduledEventEntity waitlisted = row("wait", MyRegistration.WAITLISTED, 6_000L, 7_000L);
        ScheduledEventEntity later = row("later", MyRegistration.REGISTERED, 9_000L, 10_000L);
        ScheduledEventEntity soonest = row("soon", MyRegistration.REGISTERED, 7_000L, 8_000L);

        ScheduledEventEntity picked = WidgetEventPicker.pickNext(
                Arrays.asList(ended, waitlisted, later, soonest), now);
        assertNotNull(picked);
        assertEquals("soon", picked.eventId);
    }

    @Test
    public void pickNextReturnsNullWhenNoUpcomingRegisteredEventExists() {
        assertNull(WidgetEventPicker.pickNext(Collections.emptyList(), 1_000L));
    }
}

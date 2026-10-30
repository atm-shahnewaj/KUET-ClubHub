package com.kuet.clubhub.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Participant;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class EventAnalyticsTest {

    private static Participant participant(String uid, String dept, String status,
                                           boolean checkedIn, Date updatedAt) {
        Participant p = new Participant();
        p.setUid(uid);
        p.setName("Student " + uid);
        p.setStudentId("20070" + uid);
        p.setDepartment(dept);
        p.setStatus(status);
        p.setCheckedIn(checkedIn);
        p.setUpdatedAt(updatedAt);
        return p;
    }

    @Test
    public void summarizeCountsRegisteredCheckedInMissingAndWaitlisted() {
        List<Participant> list = Arrays.asList(
                participant("1", "CSE", MyRegistration.REGISTERED, true, null),
                participant("2", "CSE", MyRegistration.REGISTERED, true, null),
                participant("3", "EEE", MyRegistration.REGISTERED, false, null),
                participant("4", "ME", MyRegistration.WAITLISTED, false, null),
                participant("5", "CE", MyRegistration.CANCELLED, false, null));

        EventAnalytics.Summary s = EventAnalytics.summarize(list, 6);
        assertEquals(3, s.registered);
        assertEquals(2, s.checkedIn);
        assertEquals(1, s.missing);
        assertEquals(1, s.waitlisted);
        assertEquals(6L, s.capacity);
        assertEquals(67, s.attendancePercent);
        assertEquals(50, s.fillPercent);
    }

    @Test
    public void departmentBreakdownSortsByCountDescendingAndNormalizesCase() {
        List<Participant> list = Arrays.asList(
                participant("1", "cse", MyRegistration.REGISTERED, false, null),
                participant("2", "CSE ", MyRegistration.REGISTERED, true, null),
                participant("3", "EEE", MyRegistration.WAITLISTED, false, null),
                participant("4", "", MyRegistration.REGISTERED, false, null),
                participant("5", "ME", MyRegistration.CANCELLED, false, null));

        List<EventAnalytics.Bucket> depts = EventAnalytics.departmentBreakdown(list);
        assertEquals(Arrays.asList("CSE", "EEE", "Other"), EventAnalytics.labels(depts));
        assertArrayEquals(new long[]{2L, 1L, 1L}, EventAnalytics.counts(depts));
    }

    @Test
    public void registrationsByDayOrdersChronologicallyAndFoldsBeyondSevenDays() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.OCTOBER, 10, 14, 30, 0);
        cal.set(Calendar.MILLISECOND, 0);

        List<Participant> list = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < 9; dayOffset++) {
            Date d = new Date(cal.getTimeInMillis() + dayOffset * 24L * 3600_000L);
            list.add(participant("u" + dayOffset, "CSE", MyRegistration.REGISTERED, false, d));
        }

        List<EventAnalytics.Bucket> timeline = EventAnalytics.registrationsByDay(list);
        assertEquals(7, timeline.size());
        assertEquals("Earlier", timeline.get(0).label);
        assertEquals(3L, timeline.get(0).count);
        assertEquals(1L, timeline.get(6).count);
    }

    @Test
    public void emptyParticipantListProducesZeroSummaryAndEmptyBuckets() {
        EventAnalytics.Summary s = EventAnalytics.summarize(Collections.emptyList(), 20);
        assertEquals(0, s.registered);
        assertEquals(0, s.checkedIn);
        assertEquals(0, s.attendancePercent);
        assertEquals(0, s.fillPercent);
        assertTrue(EventAnalytics.departmentBreakdown(Collections.emptyList()).isEmpty());
        assertTrue(EventAnalytics.registrationsByDay(Collections.emptyList()).isEmpty());
    }
}

package com.kuet.clubhub.util;

import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Participant;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure-Java aggregation for the organizer analytics screen:
 * <ul>
 *   <li>Attendance vs. registration summary (seats filled, check-in rate, no-shows, waitlist).</li>
 *   <li>Registrations over time (daily sign-up buckets).</li>
 *   <li>Department breakdown (active participants grouped by department).</li>
 * </ul>
 */
public final class EventAnalytics {

    /** Maximum number of daily bars shown before older dates are folded into "Earlier". */
    private static final int MAX_DAILY_BUCKETS = 7;

    private EventAnalytics() {
    }

    public static final class Summary {
        public final int registered;
        public final int checkedIn;
        public final int missing;
        public final int waitlisted;
        public final long capacity;
        public final int attendancePercent;
        public final int fillPercent;

        public Summary(int registered, int checkedIn, int missing, int waitlisted,
                       long capacity, int attendancePercent, int fillPercent) {
            this.registered = registered;
            this.checkedIn = checkedIn;
            this.missing = missing;
            this.waitlisted = waitlisted;
            this.capacity = capacity;
            this.attendancePercent = attendancePercent;
            this.fillPercent = fillPercent;
        }
    }

    public static final class Bucket {
        public final String label;
        public final long count;

        public Bucket(String label, long count) {
            this.label = label;
            this.count = count;
        }
    }

    public static Summary summarize(List<Participant> participants, long capacity) {
        int registered = 0;
        int checkedIn = 0;
        int waitlisted = 0;
        if (participants != null) {
            for (Participant p : participants) {
                if (p == null) {
                    continue;
                }
                if (MyRegistration.REGISTERED.equals(p.getStatus())) {
                    registered++;
                    if (p.isCheckedIn()) {
                        checkedIn++;
                    }
                } else if (MyRegistration.WAITLISTED.equals(p.getStatus())) {
                    waitlisted++;
                }
            }
        }
        int missing = Math.max(0, registered - checkedIn);
        int attendancePct = PollMath.percent(checkedIn, registered);
        int fillPct = capacity > 0 ? PollMath.percent(registered, capacity) : 0;
        return new Summary(registered, checkedIn, missing, waitlisted,
                Math.max(0, capacity), attendancePct, fillPct);
    }

    /**
     * Groups registered and waitlisted students by department (trimmed, upper-cased, with blank
     * departments grouped under "Other"), ordered from the largest department to the smallest.
     */
    public static List<Bucket> departmentBreakdown(List<Participant> participants) {
        if (participants == null || participants.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Participant p : participants) {
            if (p == null || !isActive(p.getStatus())) {
                continue;
            }
            String dept = normalizeDepartment(p.getDepartment());
            Long current = counts.get(dept);
            counts.put(dept, (current == null ? 0L : current) + 1L);
        }
        List<Bucket> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            list.add(new Bucket(entry.getKey(), entry.getValue()));
        }
        Collections.sort(list, (a, b) -> {
            int cmp = Long.compare(b.count, a.count);
            if (cmp != 0) {
                return cmp;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        return list;
    }

    /**
     * Groups active registrations by local calendar day (from {@link Participant#getUpdatedAt()}),
     * ordered chronologically. If there are more than {@link #MAX_DAILY_BUCKETS} distinct days,
     * the oldest days are combined into an initial "Earlier" bucket so the chart stays readable.
     */
    public static List<Bucket> registrationsByDay(List<Participant> participants) {
        if (participants == null || participants.isEmpty()) {
            return Collections.emptyList();
        }
        TreeMap<Long, Long> byMidnight = new TreeMap<>();
        long undated = 0;
        for (Participant p : participants) {
            if (p == null || !isActive(p.getStatus())) {
                continue;
            }
            Date d = p.getUpdatedAt();
            if (d == null) {
                undated++;
                continue;
            }
            long dayKey = localMidnight(d.getTime());
            Long current = byMidnight.get(dayKey);
            byMidnight.put(dayKey, (current == null ? 0L : current) + 1L);
        }

        SimpleDateFormat fmt = new SimpleDateFormat("d MMM", Locale.ENGLISH);
        List<Bucket> allDays = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : byMidnight.entrySet()) {
            allDays.add(new Bucket(fmt.format(new Date(entry.getKey())), entry.getValue()));
        }

        List<Bucket> result = new ArrayList<>();
        if (allDays.size() > MAX_DAILY_BUCKETS) {
            int keepRecent = MAX_DAILY_BUCKETS - 1;
            int foldCount = allDays.size() - keepRecent;
            long earlierSum = 0;
            for (int i = 0; i < foldCount; i++) {
                earlierSum += allDays.get(i).count;
            }
            result.add(new Bucket("Earlier", earlierSum));
            for (int i = foldCount; i < allDays.size(); i++) {
                result.add(allDays.get(i));
            }
        } else {
            result.addAll(allDays);
        }

        if (undated > 0 && result.isEmpty()) {
            result.add(new Bucket("Recorded", undated));
        }
        return result;
    }

    public static List<String> labels(List<Bucket> buckets) {
        List<String> out = new ArrayList<>(buckets.size());
        for (Bucket b : buckets) {
            out.add(b.label);
        }
        return out;
    }

    public static long[] counts(List<Bucket> buckets) {
        long[] out = new long[buckets.size()];
        for (int i = 0; i < buckets.size(); i++) {
            out[i] = buckets.get(i).count;
        }
        return out;
    }

    private static boolean isActive(String status) {
        return MyRegistration.REGISTERED.equals(status)
                || MyRegistration.WAITLISTED.equals(status);
    }

    static String normalizeDepartment(String raw) {
        if (raw == null) {
            return "Other";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "Other";
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static long localMidnight(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}

package com.kuet.clubhub.util;

import com.kuet.clubhub.data.local.ScheduledEventEntity;
import com.kuet.clubhub.model.MyRegistration;

import java.util.List;

/**
 * Selects the student's next upcoming registered event from the locally cached schedule rows
 * (used by the home screen widget and launcher app shortcuts).
 */
public final class WidgetEventPicker {

    private WidgetEventPicker() {
    }

    /**
     * Returns the registered event that starts soonest (or is currently in progress) and has not
     * ended yet at {@code nowMillis}, or {@code null} if none matches.
     */
    public static ScheduledEventEntity pickNext(List<ScheduledEventEntity> rows, long nowMillis) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        ScheduledEventEntity best = null;
        for (ScheduledEventEntity row : rows) {
            if (row == null || !MyRegistration.REGISTERED.equals(row.status)) {
                continue;
            }
            long end = row.endMillis > 0 ? row.endMillis : row.startMillis;
            if (end < nowMillis || row.startMillis <= 0) {
                continue;
            }
            if (best == null
                    || row.startMillis < best.startMillis
                    || (row.startMillis == best.startMillis
                        && safe(row.eventId).compareTo(safe(best.eventId)) < 0)) {
                best = row;
            }
        }
        return best;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

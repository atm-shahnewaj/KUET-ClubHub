package com.kuet.clubhub.util;

import android.content.Context;

import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Participant;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Formats and saves the organizer's participant list as an RFC-4180 CSV file so it can be shared
 * through FileProvider and an implicit Intent. Registered students who did not check in are
 * flagged as "no_show" once the event has ended (Proposal features 12 and 34).
 */
public final class CsvExporter {

    public static final String HEADER =
            "Name,Student ID,Department,Status,Attendance,Checked In At,Registered At";

    private CsvExporter() {
    }

    /**
     * Builds the CSV content for an event's participant list. Registered students come first
     * (sorted alphabetically by name), followed by the waiting list in queue order.
     */
    public static String formatParticipantsCsv(List<Participant> participants, boolean eventEnded) {
        List<Participant> registered = new ArrayList<>();
        List<Participant> waiting = new ArrayList<>();
        if (participants != null) {
            for (Participant p : participants) {
                if (p == null) {
                    continue;
                }
                if (MyRegistration.REGISTERED.equals(p.getStatus())) {
                    registered.add(p);
                } else if (MyRegistration.WAITLISTED.equals(p.getStatus())) {
                    waiting.add(p);
                }
            }
        }
        Collections.sort(registered, (a, b) -> safeLower(a.getName()).compareTo(safeLower(b.getName())));
        Collections.sort(waiting, (a, b) -> Long.compare(a.getWaitSeq(), b.getWaitSeq()));

        List<Participant> ordered = new ArrayList<>(registered.size() + waiting.size());
        ordered.addAll(registered);
        ordered.addAll(waiting);

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');

        for (Participant p : ordered) {
            String status = p.getStatus() == null ? "" : p.getStatus();
            String attendance;
            if (MyRegistration.WAITLISTED.equals(status)) {
                attendance = "waitlisted";
            } else if (p.isCheckedIn()) {
                attendance = "checked_in";
            } else if (eventEnded) {
                attendance = "no_show";
            } else {
                attendance = "not_yet";
            }
            String checkedInAt = formatTimestamp(p.getCheckedInAt(), iso);
            String registeredAt = formatTimestamp(p.getUpdatedAt(), iso);

            sb.append(escape(p.getName())).append(',')
                    .append(escape(p.getStudentId())).append(',')
                    .append(escape(p.getDepartment())).append(',')
                    .append(escape(status)).append(',')
                    .append(escape(attendance)).append(',')
                    .append(escape(checkedInAt)).append(',')
                    .append(escape(registeredAt)).append('\n');
        }
        return sb.toString();
    }

    /** Escapes a single CSV field according to RFC 4180. */
    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuotes = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Writes the CSV string into {@code files/exports/} so FileProvider can share it. */
    public static File writeParticipantsCsv(Context context, String eventId, String csvContent)
            throws IOException {
        File dir = new File(context.getFilesDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create export folder.");
        }
        String safeId = eventId == null ? "event"
                : eventId.replaceAll("[^a-zA-Z0-9_-]", "_");
        File file = new File(dir, "participants_" + safeId + ".csv");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(csvContent);
        }
        return file;
    }

    private static String formatTimestamp(Date d, SimpleDateFormat fmt) {
        return d == null ? "" : fmt.format(d);
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}

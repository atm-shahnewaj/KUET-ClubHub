package com.kuet.clubhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Participant;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CsvExporterTest {

    private static Participant make(String name, String id, String dept, String status,
                                    boolean checkedIn, long waitSeq) {
        Participant p = new Participant();
        p.setName(name);
        p.setStudentId(id);
        p.setDepartment(dept);
        p.setStatus(status);
        p.setCheckedIn(checkedIn);
        p.setWaitSeq(waitSeq);
        return p;
    }

    @Test
    public void escapeQuotesFieldsWithCommasOrQuotes() {
        assertEquals("CSE", CsvExporter.escape("CSE"));
        assertEquals("\"Khan, Amina\"", CsvExporter.escape("Khan, Amina"));
        assertEquals("\"Team \"\"Alpha\"\"\"", CsvExporter.escape("Team \"Alpha\""));
        assertEquals("", CsvExporter.escape(null));
    }

    @Test
    public void formatParticipantsCsvFlagsNoShowsAfterEventEnds() {
        List<Participant> list = Arrays.asList(
                make("Zara", "2007002", "EEE", MyRegistration.REGISTERED, false, 0),
                make("Arif, Md.", "2007001", "CSE", MyRegistration.REGISTERED, true, 0),
                make("Bina", "2007003", "ME", MyRegistration.WAITLISTED, false, 2));

        String csv = CsvExporter.formatParticipantsCsv(list, true);
        String[] lines = csv.trim().split("\n");
        assertEquals(4, lines.length);
        assertEquals(CsvExporter.HEADER, lines[0]);
        assertTrue(lines[1].startsWith("\"Arif, Md.\",2007001,CSE,registered,checked_in,"));
        assertTrue(lines[2].startsWith("Zara,2007002,EEE,registered,no_show,"));
        assertTrue(lines[3].startsWith("Bina,2007003,ME,waitlisted,waitlisted,"));
    }

    @Test
    public void formatParticipantsCsvUsesNotYetBeforeEventEnds() {
        List<Participant> list = Arrays.asList(
                make("Zara", "2007002", "EEE", MyRegistration.REGISTERED, false, 0));

        String csv = CsvExporter.formatParticipantsCsv(list, false);
        String[] lines = csv.trim().split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[1].startsWith("Zara,2007002,EEE,registered,not_yet,"));
    }
}

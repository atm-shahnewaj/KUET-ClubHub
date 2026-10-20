package com.kuet.clubhub.model;

import java.util.Date;

/** Matches the Firestore document certificates/{certId}. */
public class Certificate {

    public static final String TYPE_PARTICIPATION = "participation";

    private String certId;
    private String eventId;
    private String uid;
    private String type = TYPE_PARTICIPATION;
    private String name;
    private String studentId;
    private String eventTitle;
    private String clubName;
    private Date eventDate;
    private Date issuedAt;

    /** Required by Firestore. */
    public Certificate() {
    }

    public String getCertId() { return certId; }
    public void setCertId(String certId) { this.certId = certId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    public Date getEventDate() { return eventDate; }
    public void setEventDate(Date eventDate) { this.eventDate = eventDate; }

    public Date getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Date issuedAt) { this.issuedAt = issuedAt; }
}

package com.kuet.clubhub.model;

import java.util.Date;

/** Matches events/{eventId}/checkins/{uid}: one attendance record. */
public class CheckIn {

    private String uid;
    private String method;
    private Date timestamp;
    private String scannedBy;

    /** Required by Firestore. */
    public CheckIn() {
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getScannedBy() { return scannedBy; }
    public void setScannedBy(String scannedBy) { this.scannedBy = scannedBy; }
}

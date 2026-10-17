package com.kuet.clubhub.model;

/** Matches events/{eventId}/registrations/{uid}. */
public class Participant {

    private String uid;
    private String name;
    private String studentId;
    private String department;
    private String status;
    private long waitSeq;

    /** Required by Firestore. */
    public Participant() {
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public long getWaitSeq() { return waitSeq; }
    public void setWaitSeq(long waitSeq) { this.waitSeq = waitSeq; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

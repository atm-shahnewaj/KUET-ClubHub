package com.kuet.clubhub.model;

import java.util.ArrayList;
import java.util.List;

/** Matches events/{eventId}/seekers/{uid}: a student who posted "I need a team". */
public class Seeker {

    private String uid;
    private String name;
    private String department;
    private int year;
    private String note;
    private List<String> skills = new ArrayList<>();

    /** Required by Firestore. */
    public Seeker() {
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
}

package com.kuet.clubhub.model;

import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.List;

/** Matches the Firestore document users/{uid}. */
public class UserProfile {

    private String uid;
    private String name;
    private String studentId;
    private String department;
    private String email;
    private String role = Role.STUDENT.getValue();
    private int year;
    private long points;
    private String photoBase64;
    private List<String> skills = new ArrayList<>();
    private List<String> interests = new ArrayList<>();

    /** Required by Firestore. */
    public UserProfile() {
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public long getPoints() { return points; }
    public void setPoints(long points) { this.points = points; }

    public String getPhotoBase64() { return photoBase64; }
    public void setPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }

    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }

    @Exclude
    public Role getRoleEnum() {
        return Role.fromValue(role);
    }
}

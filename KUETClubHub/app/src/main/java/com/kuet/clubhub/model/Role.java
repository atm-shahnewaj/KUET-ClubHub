package com.kuet.clubhub.model;

public enum Role {
    STUDENT("student", "Student"),
    ORGANIZER("organizer", "Club organizer"),
    ADMIN("admin", "Admin");

    private final String value;
    private final String label;

    Role(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /** The string stored in Firestore. */
    public String getValue() {
        return value;
    }

    /** Human-readable name shown in the UI. */
    public String getLabel() {
        return label;
    }

    public static Role fromValue(String value) {
        for (Role r : values()) {
            if (r.value.equals(value)) {
                return r;
            }
        }
        return STUDENT;
    }
}

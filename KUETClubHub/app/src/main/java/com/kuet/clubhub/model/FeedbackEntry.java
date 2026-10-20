package com.kuet.clubhub.model;

import java.util.Date;

/** Matches events/{eventId}/feedback/{uid}: one rating and comment per attendee. */
public class FeedbackEntry {

    private String uid;
    private String name;
    private int rating;
    private String comment;
    private Date updatedAt;

    /** Required by Firestore. */
    public FeedbackEntry() {
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}

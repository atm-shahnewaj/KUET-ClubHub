package com.kuet.clubhub.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Matches events/{eventId}/questions/{qId}. Upvotes are kept as the list of voters' uids, so a
 * student can upvote a question only once and the number of upvotes is simply the list size.
 */
public class Question {

    private String id;
    private String text;
    private String authorUid;
    private String authorName;
    private boolean anonymous;
    private List<String> upvoterUids = new ArrayList<>();
    private boolean answered;
    private Date createdAt;

    /** Required by Firestore. */
    public Question() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getAuthorUid() { return authorUid; }
    public void setAuthorUid(String authorUid) { this.authorUid = authorUid; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }

    public List<String> getUpvoterUids() { return upvoterUids; }
    public void setUpvoterUids(List<String> upvoterUids) {
        this.upvoterUids = upvoterUids == null ? new ArrayList<>() : upvoterUids;
    }

    public boolean isAnswered() { return answered; }
    public void setAnswered(boolean answered) { this.answered = answered; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    // ---- helpers (not bean getters, so Firestore ignores them)

    public int upvotes() {
        return upvoterUids == null ? 0 : upvoterUids.size();
    }

    public boolean upvotedBy(String uid) {
        return uid != null && upvoterUids != null && upvoterUids.contains(uid);
    }

    public long createdMillis() {
        return createdAt == null ? Long.MAX_VALUE : createdAt.getTime();
    }
}

package com.kuet.clubhub.model;

import java.util.Date;

/** Matches a club announcement/notice in clubs/{clubId}/notices/{noticeId} or local cache. */
public class ClubNotice {

    private String id;
    private String clubId;
    private String title;
    private String body;
    private String authorName;
    private String authorUid;
    private boolean pinned;
    private Date createdAt;

    public ClubNotice() {
    }

    public ClubNotice(String id, String clubId, String title, String body,
                      String authorName, String authorUid, boolean pinned, Date createdAt) {
        this.id = id;
        this.clubId = clubId;
        this.title = title;
        this.body = body;
        this.authorName = authorName;
        this.authorUid = authorUid;
        this.pinned = pinned;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClubId() { return clubId; }
    public void setClubId(String clubId) { this.clubId = clubId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorUid() { return authorUid; }
    public void setAuthorUid(String authorUid) { this.authorUid = authorUid; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}

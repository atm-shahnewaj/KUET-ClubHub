package com.kuet.clubhub.model;

/**
 * Represents a message in a club's members-only group chat stored in Firebase Realtime Database
 * at club_chats/{clubId}/messages/{id}.
 */
public class ChatMessage {

    private String id;
    private String clubId;
    private String senderUid;
    private String senderName;
    private String senderDepartment;
    private String senderRole;
    private String text;
    private long timestamp;

    /** Required by Firebase Realtime Database DataSnapshot.getValue(ChatMessage.class). */
    public ChatMessage() {
    }

    public ChatMessage(String id, String clubId, String senderUid, String senderName,
                       String senderDepartment, String senderRole, String text, long timestamp) {
        this.id = id;
        this.clubId = clubId;
        this.senderUid = senderUid;
        this.senderName = senderName;
        this.senderDepartment = senderDepartment;
        this.senderRole = senderRole;
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClubId() { return clubId; }
    public void setClubId(String clubId) { this.clubId = clubId; }

    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderDepartment() { return senderDepartment; }
    public void setSenderDepartment(String senderDepartment) { this.senderDepartment = senderDepartment; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

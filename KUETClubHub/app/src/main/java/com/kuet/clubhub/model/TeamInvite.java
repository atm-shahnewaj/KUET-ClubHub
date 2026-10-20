package com.kuet.clubhub.model;

/** Matches users/{uid}/teamInvites/{teamId}: a team leader invited me. */
public class TeamInvite {

    private String teamId;
    private String eventId;
    private String eventTitle;
    private String teamName;
    private String fromUid;
    private String fromName;

    /** Required by Firestore. */
    public TeamInvite() {
    }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getFromUid() { return fromUid; }
    public void setFromUid(String fromUid) { this.fromUid = fromUid; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }
}

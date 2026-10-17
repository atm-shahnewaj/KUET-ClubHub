package com.kuet.clubhub.model;

import java.util.Date;

/** Matches users/{uid}/registrations/{eventId}: my registration plus a summary of the event. */
public class MyRegistration {

    public static final String REGISTERED = "registered";
    public static final String WAITLISTED = "waitlisted";
    public static final String CANCELLED = "cancelled";

    private String eventId;
    private String title;
    private String clubName;
    private String type = EventType.OTHER.getValue();
    private Date startTime;
    private Date endTime;
    private String venue;
    private String status;
    private long waitSeq;

    /** Required by Firestore. */
    public MyRegistration() {
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getWaitSeq() { return waitSeq; }
    public void setWaitSeq(long waitSeq) { this.waitSeq = waitSeq; }

    public boolean isActive() {
        return REGISTERED.equals(status) || WAITLISTED.equals(status);
    }

    /** A light Event built from the summary, so the same list adapter can show it. */
    public Event toEvent() {
        Event e = new Event();
        e.setId(eventId);
        e.setTitle(title);
        e.setClubName(clubName);
        e.setType(type);
        e.setStartTime(startTime);
        e.setEndTime(endTime);
        e.setVenue(venue);
        return e;
    }
}

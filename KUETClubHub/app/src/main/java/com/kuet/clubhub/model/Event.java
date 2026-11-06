package com.kuet.clubhub.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Matches the Firestore document events/{eventId}. */
public class Event {

    private String id;
    private String clubId;
    private String clubName;
    private String title;
    private String description;
    private String type = EventType.OTHER.getValue();
    private String bannerBase64;
    private List<String> tags = new ArrayList<>();
    private List<String> organizers = new ArrayList<>();
    private Date startTime;
    private Date endTime;
    private String venue;
    private long capacity;
    private long registeredCount;
    private long waitlistCount;
    private long waitHead;
    private long waitTail;
    private int teamSizeMin;
    private int teamSizeMax;
    private Date registrationDeadline;
    private String prizes;
    private String rules;
    private String createdBy;

    /** Required by Firestore. */
    public Event() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClubId() { return clubId; }
    public void setClubId(String clubId) { this.clubId = clubId; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getBannerBase64() { return bannerBase64; }
    public void setBannerBase64(String bannerBase64) { this.bannerBase64 = bannerBase64; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getOrganizers() { return organizers; }
    public void setOrganizers(List<String> organizers) { this.organizers = organizers; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public long getCapacity() { return capacity; }
    public void setCapacity(long capacity) { this.capacity = capacity; }

    public long getRegisteredCount() { return registeredCount; }
    public void setRegisteredCount(long registeredCount) { this.registeredCount = registeredCount; }

    public long getWaitlistCount() { return waitlistCount; }
    public void setWaitlistCount(long waitlistCount) { this.waitlistCount = waitlistCount; }

    public long getWaitHead() { return waitHead; }
    public void setWaitHead(long waitHead) { this.waitHead = waitHead; }

    public long getWaitTail() { return waitTail; }
    public void setWaitTail(long waitTail) { this.waitTail = waitTail; }

    public int getTeamSizeMin() { return teamSizeMin; }
    public void setTeamSizeMin(int teamSizeMin) { this.teamSizeMin = teamSizeMin; }

    public int getTeamSizeMax() { return teamSizeMax; }
    public void setTeamSizeMax(int teamSizeMax) { this.teamSizeMax = teamSizeMax; }

    public Date getRegistrationDeadline() { return registrationDeadline; }
    public void setRegistrationDeadline(Date registrationDeadline) { this.registrationDeadline = registrationDeadline; }

    public String getPrizes() { return prizes; }
    public void setPrizes(String prizes) { this.prizes = prizes; }

    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public EventType typeEnum() {
        return EventType.fromValue(type);
    }

    public long seatsLeft() {
        return Math.max(0, capacity - registeredCount);
    }

    public boolean hasOrganizer(String uid) {
        return uid != null && organizers != null && organizers.contains(uid);
    }

    public boolean hasEnded(long now) {
        Date end = endTime != null ? endTime : startTime;
        return end != null && now > end.getTime();
    }

    public boolean registrationClosed(long now) {
        if (startTime != null && now >= startTime.getTime()) {
            return true;
        }
        return registrationDeadline != null && now > registrationDeadline.getTime();
    }
}

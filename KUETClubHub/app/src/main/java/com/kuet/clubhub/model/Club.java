package com.kuet.clubhub.model;

import java.util.ArrayList;
import java.util.List;

/** Matches the Firestore document clubs/{clubId}. */
public class Club {

    private String id;
    private String name;
    private String category;
    private String description;
    private List<String> organizers = new ArrayList<>();
    private long memberCount;
    private boolean approved;

    /** Required by Firestore. */
    public Club() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getOrganizers() { return organizers; }
    public void setOrganizers(List<String> organizers) { this.organizers = organizers; }

    public long getMemberCount() { return memberCount; }
    public void setMemberCount(long memberCount) { this.memberCount = memberCount; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public boolean hasOrganizer(String uid) {
        return uid != null && organizers != null && organizers.contains(uid);
    }
}

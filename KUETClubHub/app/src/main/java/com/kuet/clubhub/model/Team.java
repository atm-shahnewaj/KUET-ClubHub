package com.kuet.clubhub.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Matches the Firestore document teams/{teamId}. */
public class Team {

    private String id;
    private String eventId;
    private String eventTitle;
    private String name;
    private String leaderUid;
    private String leaderName;
    private String note;
    private boolean open;
    private int minSize;
    private int maxSize;
    private List<String> memberUids = new ArrayList<>();
    private Map<String, String> memberNames = new HashMap<>();
    private Map<String, List<String>> memberSkills = new HashMap<>();
    private List<String> skillsNeeded = new ArrayList<>();

    /** Required by Firestore. */
    public Team() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLeaderUid() { return leaderUid; }
    public void setLeaderUid(String leaderUid) { this.leaderUid = leaderUid; }

    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }

    public int getMinSize() { return minSize; }
    public void setMinSize(int minSize) { this.minSize = minSize; }

    public int getMaxSize() { return maxSize; }
    public void setMaxSize(int maxSize) { this.maxSize = maxSize; }

    public List<String> getMemberUids() { return memberUids; }
    public void setMemberUids(List<String> memberUids) { this.memberUids = memberUids; }

    public Map<String, String> getMemberNames() { return memberNames; }
    public void setMemberNames(Map<String, String> memberNames) { this.memberNames = memberNames; }

    public Map<String, List<String>> getMemberSkills() { return memberSkills; }
    public void setMemberSkills(Map<String, List<String>> memberSkills) { this.memberSkills = memberSkills; }

    public List<String> getSkillsNeeded() { return skillsNeeded; }
    public void setSkillsNeeded(List<String> skillsNeeded) { this.skillsNeeded = skillsNeeded; }

    // ---- helpers (not bean getters, so Firestore ignores them)

    public int size() {
        return memberUids == null ? 0 : memberUids.size();
    }

    public boolean full() {
        return maxSize > 0 && size() >= maxSize;
    }

    /** True once the team has at least the minimum number of members the event asks for. */
    public boolean complete() {
        return size() >= Math.max(1, minSize);
    }

    public boolean hasMember(String uid) {
        return uid != null && memberUids != null && memberUids.contains(uid);
    }

    public String nameOf(String uid) {
        String n = memberNames == null ? null : memberNames.get(uid);
        return n == null ? "" : n;
    }

    /** Every skill (lower-case) that at least one current member has. */
    public Set<String> coveredSkills() {
        Set<String> out = new HashSet<>();
        if (memberSkills != null) {
            for (List<String> skills : memberSkills.values()) {
                if (skills != null) {
                    for (String s : skills) {
                        if (s != null && !s.trim().isEmpty()) {
                            out.add(s.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        return out;
    }
}

package com.kuet.clubhub.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches events/{eventId}/polls/{pollId}. The vote totals live on the poll itself as
 * counts{"0": n, "1": n, ...} (the key is the option's position), so a listening phone reads one
 * small document instead of every single vote.
 */
public class Poll {

    public static final String OPEN = "open";
    public static final String CLOSED = "closed";

    private String id;
    private String question;
    private List<String> options = new ArrayList<>();
    private Map<String, Long> counts = new HashMap<>();
    private String status = OPEN;
    private String createdBy;
    private Date createdAt;

    /** Required by Firestore. */
    public Poll() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options == null ? new ArrayList<>() : options; }

    public Map<String, Long> getCounts() { return counts; }
    public void setCounts(Map<String, Long> counts) { this.counts = counts == null ? new HashMap<>() : counts; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    // ---- helpers (not bean getters, so Firestore ignores them)

    /** True while people can still vote. */
    public boolean acceptingVotes() {
        return !CLOSED.equals(status);
    }

    /** Votes for the option at this position. */
    public long votesFor(int index) {
        Long n = counts == null ? null : counts.get(String.valueOf(index));
        return n == null ? 0 : Math.max(0, n);
    }

    public long totalVotes() {
        long sum = 0;
        for (int i = 0; i < options.size(); i++) {
            sum += votesFor(i);
        }
        return sum;
    }

    /** The votes of every option, in option order. */
    public long[] tally() {
        long[] result = new long[options.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = votesFor(i);
        }
        return result;
    }

    /** The empty tally written when a poll is created: every option starts at 0. */
    public static Map<String, Object> zeroCounts(int optionCount) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < optionCount; i++) {
            m.put(String.valueOf(i), 0L);
        }
        return m;
    }
}

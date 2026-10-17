package com.kuet.clubhub.data.local;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.kuet.clubhub.model.Event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/** Room row for one event (offline cache of the Firestore events collection). */
@Entity(tableName = "events")
public class EventEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String clubId;
    public String clubName;
    public String title;
    public String description;
    public String type;
    public String tagsCsv;
    public String organizersCsv;
    public String venue;
    public String prizes;
    public String rules;
    public String createdBy;
    public long startMillis;
    public long endMillis;
    /** 0 means "no deadline". */
    public long deadlineMillis;
    public long capacity;
    public long registeredCount;
    public long waitlistCount;
    public long waitHead;
    public long waitTail;
    public int teamSizeMin;
    public int teamSizeMax;

    public static EventEntity fromEvent(Event e) {
        EventEntity x = new EventEntity();
        x.id = e.getId() == null ? "" : e.getId();
        x.clubId = e.getClubId();
        x.clubName = e.getClubName();
        x.title = e.getTitle();
        x.description = e.getDescription();
        x.type = e.getType();
        x.tagsCsv = join(e.getTags());
        x.organizersCsv = join(e.getOrganizers());
        x.venue = e.getVenue();
        x.prizes = e.getPrizes();
        x.rules = e.getRules();
        x.createdBy = e.getCreatedBy();
        x.startMillis = e.getStartTime() == null ? 0 : e.getStartTime().getTime();
        x.endMillis = e.getEndTime() == null ? 0 : e.getEndTime().getTime();
        x.deadlineMillis = e.getRegistrationDeadline() == null
                ? 0 : e.getRegistrationDeadline().getTime();
        x.capacity = e.getCapacity();
        x.registeredCount = e.getRegisteredCount();
        x.waitlistCount = e.getWaitlistCount();
        x.waitHead = e.getWaitHead();
        x.waitTail = e.getWaitTail();
        x.teamSizeMin = e.getTeamSizeMin();
        x.teamSizeMax = e.getTeamSizeMax();
        return x;
    }

    public Event toEvent() {
        Event e = new Event();
        e.setId(id);
        e.setClubId(clubId);
        e.setClubName(clubName);
        e.setTitle(title);
        e.setDescription(description);
        e.setType(type);
        e.setTags(split(tagsCsv));
        e.setOrganizers(split(organizersCsv));
        e.setVenue(venue);
        e.setPrizes(prizes);
        e.setRules(rules);
        e.setCreatedBy(createdBy);
        e.setStartTime(startMillis == 0 ? null : new Date(startMillis));
        e.setEndTime(endMillis == 0 ? null : new Date(endMillis));
        e.setRegistrationDeadline(deadlineMillis == 0 ? null : new Date(deadlineMillis));
        e.setCapacity(capacity);
        e.setRegisteredCount(registeredCount);
        e.setWaitlistCount(waitlistCount);
        e.setWaitHead(waitHead);
        e.setWaitTail(waitTail);
        e.setTeamSizeMin(teamSizeMin);
        e.setTeamSizeMax(teamSizeMax);
        return e;
    }

    private static String join(List<String> list) {
        return list == null ? "" : TextUtils.join(",", list);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(csv.split(",")));
    }
}

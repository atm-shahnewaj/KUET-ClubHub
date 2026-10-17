package com.kuet.clubhub.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.kuet.clubhub.model.Club;

import java.util.ArrayList;
import java.util.Arrays;

/** Room row for one approved club (offline cache of the Firestore clubs collection). */
@Entity(tableName = "clubs")
public class ClubEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String name;
    public String category;
    public String description;
    /** Organizer uids joined with commas. */
    public String organizersCsv;
    public long memberCount;

    public static ClubEntity fromClub(Club club) {
        ClubEntity e = new ClubEntity();
        e.id = club.getId() == null ? "" : club.getId();
        e.name = club.getName();
        e.category = club.getCategory();
        e.description = club.getDescription();
        e.organizersCsv = club.getOrganizers() == null ? ""
                : android.text.TextUtils.join(",", club.getOrganizers());
        e.memberCount = club.getMemberCount();
        return e;
    }

    public Club toClub() {
        Club c = new Club();
        c.setId(id);
        c.setName(name);
        c.setCategory(category);
        c.setDescription(description);
        c.setMemberCount(memberCount);
        c.setApproved(true); // only approved clubs are cached
        if (organizersCsv == null || organizersCsv.isEmpty()) {
            c.setOrganizers(new ArrayList<>());
        } else {
            c.setOrganizers(new ArrayList<>(Arrays.asList(organizersCsv.split(","))));
        }
        return c;
    }
}

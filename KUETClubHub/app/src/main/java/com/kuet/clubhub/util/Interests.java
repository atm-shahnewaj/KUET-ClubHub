package com.kuet.clubhub.util;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Interest tags: the suggested list, parsing of typed tags, and matching against event tags. */
public final class Interests {

    public static final String[] SUGGESTED = {
            "robotics", "ai", "programming", "web", "mobile", "cybersecurity", "electronics",
            "design", "career", "sports", "cultural", "debate", "photography", "entrepreneurship"
    };

    private Interests() {
    }

    /** "Robotics, #AI  design" -> [robotics, ai, design] (lower-case, no duplicates, at most max). */
    public static List<String> parse(String text, int max) {
        Set<String> tags = new LinkedHashSet<>();
        if (text != null) {
            for (String part : text.split("[,\\s]+")) {
                String tag = part.trim().toLowerCase(Locale.ROOT);
                if (tag.startsWith("#")) {
                    tag = tag.substring(1);
                }
                if (!tag.isEmpty() && tags.size() < max) {
                    tags.add(tag);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    public static String join(List<String> tags) {
        return tags == null ? "" : TextUtils.join(", ", tags);
    }

    /** The event tags that equal one of the person's interests (case-insensitive). */
    public static List<String> matched(List<String> interests, List<String> eventTags) {
        List<String> out = new ArrayList<>();
        if (interests == null || eventTags == null) {
            return out;
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String i : interests) {
            if (i != null) {
                wanted.add(i.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (String t : eventTags) {
            if (t != null && wanted.contains(t.trim().toLowerCase(Locale.ROOT)) && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }
}

package com.kuet.clubhub.model;

import com.kuet.clubhub.R;

public enum EventType {
    WORKSHOP("workshop", "Workshop", R.color.type_workshop),
    SEMINAR("seminar", "Seminar", R.color.type_seminar),
    COMPETITION("competition", "Competition", R.color.type_competition),
    OTHER("other", "Other", R.color.type_other);

    private final String value;
    private final String label;
    private final int colorRes;

    EventType(String value, String label, int colorRes) {
        this.value = value;
        this.label = label;
        this.colorRes = colorRes;
    }

    public String getValue() { return value; }

    public String getLabel() { return label; }

    public int getColorRes() { return colorRes; }

    public static EventType fromValue(String value) {
        for (EventType t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        return OTHER;
    }

    public static EventType fromLabel(String label) {
        for (EventType t : values()) {
            if (t.label.equals(label)) {
                return t;
            }
        }
        return null;
    }

    public static String[] labels() {
        EventType[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].label;
        }
        return out;
    }
}

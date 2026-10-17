package com.kuet.clubhub.util;

import java.util.Arrays;

public final class Years {

    public static final String[] LABELS = {"1st year", "2nd year", "3rd year", "4th year"};

    private Years() {
    }

    /** 1..4 -> label; anything else -> a dash. */
    public static String label(int year) {
        if (year >= 1 && year <= LABELS.length) {
            return LABELS[year - 1];
        }
        return "-";
    }

    /** Label -> 1..4, or 0 when the text is not a valid label. */
    public static int fromLabel(String label) {
        int index = Arrays.asList(LABELS).indexOf(label);
        return index + 1;
    }
}

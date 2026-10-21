package com.kuet.clubhub.util;

/** Small, testable maths for the poll bar chart. */
public final class PollMath {

    private PollMath() {
    }

    /** Share of the total, 0 to 100, rounded to a whole percent. 0 when nobody has voted. */
    public static int percent(long votes, long total) {
        if (total <= 0 || votes <= 0) {
            return 0;
        }
        return (int) Math.round(100.0 * Math.min(votes, total) / total);
    }

    /** Bar length as a fraction (0 to 1) of the track. */
    public static float fraction(long votes, long total) {
        if (total <= 0 || votes <= 0) {
            return 0f;
        }
        return Math.min(1f, votes / (float) total);
    }

    /** Position of the option with the most votes, or -1 when there are no votes or it is a tie. */
    public static int leader(long[] tally) {
        int best = -1;
        long max = 0;
        boolean tie = false;
        for (int i = 0; i < tally.length; i++) {
            if (tally[i] > max) {
                max = tally[i];
                best = i;
                tie = false;
            } else if (tally[i] == max && max > 0) {
                tie = true;
            }
        }
        return tie ? -1 : best;
    }
}

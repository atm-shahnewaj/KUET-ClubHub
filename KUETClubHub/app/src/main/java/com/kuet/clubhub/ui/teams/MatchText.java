package com.kuet.clubhub.ui.teams;

import android.content.Context;

import com.kuet.clubhub.R;
import com.kuet.clubhub.util.Interests;
import com.kuet.clubhub.util.TeamMatcher;

/** Turns a matcher result into a short explanation, so the score is never a mystery. */
final class MatchText {

    private MatchText() {
    }

    /** For example "Covers: java, design · Adds: figma". Empty when there is nothing to say. */
    static String explain(Context context, TeamMatcher.Match m) {
        StringBuilder sb = new StringBuilder();
        if (!m.covers.isEmpty()) {
            sb.append(context.getString(R.string.match_covers, Interests.join(m.covers)));
        }
        if (!m.adds.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(context.getString(R.string.match_adds, Interests.join(m.adds)));
        }
        return sb.toString();
    }
}

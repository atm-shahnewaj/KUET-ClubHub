package com.kuet.clubhub.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The skill-based team matcher. The score is deliberately simple so it can be explained:
 *
 *   +10 for every skill the team asked for that the candidate has
 *   + 2 for every skill the candidate has that nobody in the team has yet
 *
 * Pure Java (no Android classes), so it is covered by an ordinary unit test.
 */
public final class TeamMatcher {

    public static final int POINTS_PER_NEEDED_SKILL = 10;
    public static final int POINTS_PER_NEW_SKILL = 2;

    /** One scored candidate, with the reasons behind the number. */
    public static final class Match {
        public final int score;
        /** Needed skills the candidate covers. */
        public final List<String> covers;
        /** Extra skills the candidate would add to the team. */
        public final List<String> adds;

        Match(int score, List<String> covers, List<String> adds) {
            this.score = score;
            this.covers = covers;
            this.adds = adds;
        }
    }

    private TeamMatcher() {
    }

    /**
     * @param candidateSkills skills of the person being considered
     * @param needed          skills the team is looking for
     * @param teamHas         skills the current members already have
     */
    public static Match score(Collection<String> candidateSkills, Collection<String> needed,
                              Collection<String> teamHas) {
        Set<String> mine = normalize(candidateSkills);
        Set<String> wanted = normalize(needed);
        Set<String> have = normalize(teamHas);

        List<String> covers = new ArrayList<>();
        for (String s : wanted) {
            if (mine.contains(s)) {
                covers.add(s);
            }
        }
        List<String> adds = new ArrayList<>();
        for (String s : mine) {
            // A needed skill is counted once, as "covers"; "adds" is only for the extras.
            if (!have.contains(s) && !wanted.contains(s)) {
                adds.add(s);
            }
        }
        int score = covers.size() * POINTS_PER_NEEDED_SKILL + adds.size() * POINTS_PER_NEW_SKILL;
        return new Match(score, covers, adds);
    }

    /** Lower-cases, trims and removes blanks and duplicates, keeping the original order. */
    static Set<String> normalize(Collection<String> skills) {
        Set<String> out = new LinkedHashSet<>();
        if (skills != null) {
            for (String s : skills) {
                if (s != null) {
                    String t = s.trim().toLowerCase(Locale.ROOT);
                    if (!t.isEmpty()) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }
}

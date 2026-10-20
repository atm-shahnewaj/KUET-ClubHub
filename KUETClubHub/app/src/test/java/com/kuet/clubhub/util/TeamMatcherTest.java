package com.kuet.clubhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class TeamMatcherTest {

    @Test
    public void neededSkillsScoreTenEach() {
        TeamMatcher.Match m = TeamMatcher.score(
                Arrays.asList("Java", "Design"),
                Arrays.asList("design", "java", "pitching"),
                Collections.<String>emptyList());
        assertEquals(20, m.score);
        assertEquals(Arrays.asList("design", "java"), m.covers);
        assertTrue(m.adds.isEmpty());
    }

    @Test
    public void newSkillsScoreTwoEachButSkillsTheTeamHasDoNot() {
        TeamMatcher.Match m = TeamMatcher.score(
                Arrays.asList("arduino", "java", "figma"),
                Collections.<String>emptyList(),
                Arrays.asList("java"));
        assertEquals(4, m.score);
        assertEquals(Arrays.asList("arduino", "figma"), m.adds);
    }

    @Test
    public void neededSkillIsNotCountedTwice() {
        TeamMatcher.Match m = TeamMatcher.score(
                Arrays.asList("java"), Arrays.asList("java"), Collections.<String>emptyList());
        assertEquals(10, m.score);
        assertTrue(m.adds.isEmpty());
    }

    @Test
    public void blanksNullsAndDuplicatesAreIgnored() {
        TeamMatcher.Match m = TeamMatcher.score(
                Arrays.asList(" Java ", "java", "", null),
                Arrays.asList("JAVA", " "), null);
        assertEquals(10, m.score);
        assertEquals(1, m.covers.size());
    }

    @Test
    public void nothingInCommonScoresZero() {
        TeamMatcher.Match m = TeamMatcher.score(
                Collections.<String>emptyList(), Arrays.asList("java"), Arrays.asList("java"));
        assertEquals(0, m.score);
    }
}

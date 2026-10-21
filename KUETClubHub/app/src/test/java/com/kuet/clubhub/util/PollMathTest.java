package com.kuet.clubhub.util;

import static org.junit.Assert.assertEquals;

import com.kuet.clubhub.model.Poll;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PollMathTest {

    @Test
    public void percentIsZeroWhenNobodyVoted() {
        assertEquals(0, PollMath.percent(0, 0));
        assertEquals(0f, PollMath.fraction(0, 0), 0f);
    }

    @Test
    public void percentRoundsToAWholeNumber() {
        assertEquals(33, PollMath.percent(1, 3));
        assertEquals(67, PollMath.percent(2, 3));
        assertEquals(100, PollMath.percent(5, 5));
        assertEquals(50, PollMath.percent(1, 2));
    }

    @Test
    public void fractionIsClampedToOne() {
        assertEquals(0.25f, PollMath.fraction(1, 4), 0.0001f);
        assertEquals(1f, PollMath.fraction(9, 4), 0f);
    }

    @Test
    public void leaderIsTheOptionWithTheMostVotes() {
        assertEquals(1, PollMath.leader(new long[]{2, 7, 3}));
    }

    @Test
    public void leaderIsMinusOneForNoVotesOrATie() {
        assertEquals(-1, PollMath.leader(new long[]{0, 0, 0}));
        assertEquals(-1, PollMath.leader(new long[]{4, 4, 1}));
        assertEquals(-1, PollMath.leader(new long[0]));
    }

    @Test
    public void pollTalliesFollowTheOptionOrderAndTreatMissingKeysAsZero() {
        Poll poll = new Poll();
        poll.setOptions(Arrays.asList("Yes", "No", "Maybe"));
        Map<String, Long> counts = new HashMap<>();
        counts.put("0", 4L);
        counts.put("2", 1L);
        poll.setCounts(counts);
        assertEquals(Arrays.toString(new long[]{4, 0, 1}), Arrays.toString(poll.tally()));
        assertEquals(5, poll.totalVotes());
    }

    @Test
    public void aNewPollStartsWithEveryOptionAtZero() {
        Map<String, Object> zero = Poll.zeroCounts(3);
        assertEquals(3, zero.size());
        assertEquals(0L, zero.get("0"));
        assertEquals(0L, zero.get("2"));
    }

    @Test
    public void aClosedPollNoLongerAcceptsVotes() {
        Poll poll = new Poll();
        assertEquals(true, poll.acceptingVotes());
        poll.setStatus(Poll.CLOSED);
        assertEquals(false, poll.acceptingVotes());
    }
}

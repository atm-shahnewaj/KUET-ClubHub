package com.kuet.clubhub.util;

import static org.junit.Assert.assertEquals;

import com.kuet.clubhub.model.Question;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class QuestionRankerTest {

    private static Question q(String id, int upvotes, boolean answered, long createdAt) {
        Question q = new Question();
        q.setId(id);
        q.setAnswered(answered);
        q.setCreatedAt(new Date(createdAt));
        List<String> voters = new ArrayList<>();
        for (int i = 0; i < upvotes; i++) {
            voters.add("u" + i);
        }
        q.setUpvoterUids(voters);
        return q;
    }

    private static List<String> ids(List<Question> list) {
        List<String> ids = new ArrayList<>();
        for (Question q : list) {
            ids.add(q.getId());
        }
        return ids;
    }

    @Test
    public void mostUpvotedComesFirst() {
        List<Question> ranked = QuestionRanker.rank(Arrays.asList(
                q("a", 1, false, 100), q("b", 5, false, 200), q("c", 3, false, 300)));
        assertEquals(Arrays.asList("b", "c", "a"), ids(ranked));
    }

    @Test
    public void equalUpvotesKeepTheEarlierQuestionFirst() {
        List<Question> ranked = QuestionRanker.rank(Arrays.asList(
                q("late", 2, false, 900), q("early", 2, false, 100)));
        assertEquals(Arrays.asList("early", "late"), ids(ranked));
    }

    @Test
    public void answeredQuestionsSinkToTheBottomEvenWithManyUpvotes() {
        List<Question> ranked = QuestionRanker.rank(Arrays.asList(
                q("done", 50, true, 100), q("new", 0, false, 200), q("mid", 4, false, 300)));
        assertEquals(Arrays.asList("mid", "new", "done"), ids(ranked));
    }

    @Test
    public void aQuestionStillWaitingForItsServerTimeCountsAsNewest() {
        Question pending = q("pending", 1, false, 0);
        pending.setCreatedAt(null);
        List<Question> ranked = QuestionRanker.rank(Arrays.asList(pending, q("old", 1, false, 100)));
        assertEquals(Arrays.asList("old", "pending"), ids(ranked));
    }

    @Test
    public void rankingDoesNotChangeTheInputList() {
        List<Question> input = Arrays.asList(q("a", 1, false, 1), q("b", 2, false, 2));
        QuestionRanker.rank(input);
        assertEquals(Arrays.asList("a", "b"), ids(input));
    }

    @Test
    public void upvotedByLooksAtTheVoterList() {
        Question q = q("a", 2, false, 1);
        assertEquals(true, q.upvotedBy("u1"));
        assertEquals(false, q.upvotedBy("someone-else"));
        assertEquals(false, q.upvotedBy(null));
        assertEquals(2, q.upvotes());
    }
}

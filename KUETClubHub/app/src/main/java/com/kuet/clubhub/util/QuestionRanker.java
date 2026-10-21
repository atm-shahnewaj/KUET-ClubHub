package com.kuet.clubhub.util;

import com.kuet.clubhub.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Orders the Q&A board so the questions worth answering next rise to the top: unanswered ones
 * first, the most upvoted first, and for equal upvotes the one asked earlier. Answered questions
 * sink to the bottom. Pure Java, covered by a unit test.
 */
public final class QuestionRanker {

    private static final Comparator<Question> ORDER = (a, b) -> {
        if (a.isAnswered() != b.isAnswered()) {
            return a.isAnswered() ? 1 : -1;
        }
        if (a.upvotes() != b.upvotes()) {
            return Integer.compare(b.upvotes(), a.upvotes());
        }
        int byTime = Long.compare(a.createdMillis(), b.createdMillis());
        if (byTime != 0) {
            return byTime;
        }
        String ia = a.getId() == null ? "" : a.getId();
        String ib = b.getId() == null ? "" : b.getId();
        return ia.compareTo(ib);
    };

    private QuestionRanker() {
    }

    public static List<Question> rank(List<Question> questions) {
        List<Question> sorted = new ArrayList<>(questions);
        Collections.sort(sorted, ORDER);
        return sorted;
    }
}

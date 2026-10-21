package com.kuet.clubhub.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.kuet.clubhub.model.Poll;
import com.kuet.clubhub.model.Question;
import com.kuet.clubhub.model.UserProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the live part of an event needs, in one place:
 * <ul>
 *   <li>polls at events/{eventId}/polls/{pollId}, with the running totals in counts{};</li>
 *   <li>one vote document per student per poll at events/{eventId}/pollVotes/{pollId}_{uid}
 *       (create-only, so a student can vote once and cannot change the vote);</li>
 *   <li>questions at events/{eventId}/questions/{qId}, upvotes kept as a list of voter uids.</li>
 * </ul>
 * Every listener is a Firestore real-time listener, so results and the question order update on
 * all phones as soon as someone votes. The Firestore rules decide who may do what; the messages
 * below only explain a refusal.
 */
public final class LiveRepository {

    private static final String EVENTS = "events";
    private static final String POLLS = "polls";
    private static final String POLL_VOTES = "pollVotes";
    private static final String QUESTIONS = "questions";

    public static final int MAX_OPTIONS = 4;
    public static final int MAX_QUESTION_LENGTH = 200;
    public static final int MAX_POLL_QUESTION_LENGTH = 120;
    public static final int MAX_OPTION_LENGTH = 60;

    public interface PollsListener {
        void onPolls(List<Poll> polls);

        void onError(String message);
    }

    /** pollId -> the option position this student voted for. */
    public interface MyVotesListener {
        void onVotes(Map<String, Integer> votes);

        void onError(String message);
    }

    public interface QuestionsListener {
        void onQuestions(List<Question> questions);

        void onError(String message);
    }

    private LiveRepository() {
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static DocumentReference event(String eventId) {
        return db().collection(EVENTS).document(eventId);
    }

    private static DocumentReference pollRef(String eventId, String pollId) {
        return event(eventId).collection(POLLS).document(pollId);
    }

    private static DocumentReference questionRef(String eventId, String questionId) {
        return event(eventId).collection(QUESTIONS).document(questionId);
    }

    /** Document id of one student's vote in one poll. Firestore ids make a second vote impossible. */
    public static String voteId(String pollId, String uid) {
        return pollId + "_" + uid;
    }

    // ------------------------------------------------------------------ polls

    public static ListenerRegistration listenPolls(String eventId, PollsListener listener) {
        return event(eventId).collection(POLLS).addSnapshotListener((snap, error) -> {
            if (error != null) {
                listener.onError(describe(error, "You cannot see the polls of this event."));
                return;
            }
            if (snap == null) {
                return;
            }
            List<Poll> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Poll p = d.toObject(Poll.class, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                if (p != null) {
                    p.setId(d.getId());
                    list.add(p);
                }
            }
            listener.onPolls(list);
        });
    }

    /** The student's own votes in this event (one query, however many polls there are). */
    public static ListenerRegistration listenMyVotes(String eventId, String uid,
                                                     MyVotesListener listener) {
        return event(eventId).collection(POLL_VOTES).whereEqualTo("uid", uid)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        listener.onError(describe(error, "Could not load your votes."));
                        return;
                    }
                    if (snap == null) {
                        return;
                    }
                    Map<String, Integer> votes = new HashMap<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String pollId = d.getString("pollId");
                        String option = d.getString("option");
                        if (pollId == null || option == null) {
                            continue;
                        }
                        try {
                            votes.put(pollId, Integer.parseInt(option));
                        } catch (NumberFormatException ignored) {
                            // not one of ours; skip it
                        }
                    }
                    listener.onVotes(votes);
                });
    }

    /** Organizer: launches a poll. The options are already trimmed and checked by the screen. */
    public static void createPoll(String eventId, UserProfile organizer, String question,
                                  List<String> options, Callback<String> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("question", question);
        data.put("options", options);
        data.put("counts", Poll.zeroCounts(options.size()));
        data.put("status", Poll.OPEN);
        data.put("createdBy", organizer.getUid());
        data.put("createdAt", FieldValue.serverTimestamp());
        event(eventId).collection(POLLS).add(data).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                cb.onSuccess(task.getResult().getId());
            } else {
                cb.onError(describe(task.getException(),
                        "Only the organizers of this event can launch polls."));
            }
        });
    }

    /** Organizer: stops (or reopens) voting. The results stay visible either way. */
    public static void setPollOpen(String eventId, String pollId, boolean open, Callback<Void> cb) {
        pollRef(eventId, pollId).update("status", open ? Poll.OPEN : Poll.CLOSED)
                .addOnCompleteListener(task -> finish(task.isSuccessful(), task.getException(),
                        "Only the organizers of this event can change a poll.", cb));
    }

    public static void deletePoll(String eventId, String pollId, Callback<Void> cb) {
        pollRef(eventId, pollId).delete()
                .addOnCompleteListener(task -> finish(task.isSuccessful(), task.getException(),
                        "Only the organizers of this event can delete a poll.", cb));
    }

    /**
     * Casts one vote. The vote document and the +1 on the option's total are written in one batch,
     * and the rules insist on both together, so the totals can never drift from the votes.
     */
    public static void vote(String eventId, String pollId, String uid, int optionIndex,
                            Callback<Void> cb) {
        Map<String, Object> vote = new HashMap<>();
        vote.put("pollId", pollId);
        vote.put("uid", uid);
        vote.put("option", String.valueOf(optionIndex));
        vote.put("votedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db().batch();
        batch.set(event(eventId).collection(POLL_VOTES).document(voteId(pollId, uid)), vote);
        batch.update(pollRef(eventId, pollId), "counts." + optionIndex, FieldValue.increment(1));
        batch.commit().addOnCompleteListener(task -> finish(task.isSuccessful(), task.getException(),
                "You can vote once, only after you have checked in, and only while the poll is open.",
                cb));
    }

    // ------------------------------------------------------------------ Q&A

    public static ListenerRegistration listenQuestions(String eventId, QuestionsListener listener) {
        CollectionReference col = event(eventId).collection(QUESTIONS);
        return col.addSnapshotListener((snap, error) -> {
            if (error != null) {
                listener.onError(describe(error, "You cannot see the questions of this event."));
                return;
            }
            if (snap == null) {
                return;
            }
            List<Question> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Question q = d.toObject(Question.class, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                if (q != null) {
                    q.setId(d.getId());
                    list.add(q);
                }
            }
            listener.onQuestions(Collections.unmodifiableList(list));
        });
    }

    /** Posts a question. With anonymous = true the name is left out; only the organizers' tools see the uid. */
    public static void ask(String eventId, UserProfile user, String text, boolean anonymous,
                           Callback<String> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("text", text);
        data.put("authorUid", user.getUid());
        data.put("authorName", anonymous ? "" : user.getName());
        data.put("anonymous", anonymous);
        data.put("upvoterUids", new ArrayList<String>());
        data.put("answered", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        event(eventId).collection(QUESTIONS).add(data).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                cb.onSuccess(task.getResult().getId());
            } else {
                cb.onError(describe(task.getException(),
                        "Only attendees who have checked in can ask questions."));
            }
        });
    }

    /** Adds or takes back my upvote. Array union / remove are atomic, so two taps never double-count. */
    public static void setUpvote(String eventId, String questionId, String uid, boolean upvote,
                                 Callback<Void> cb) {
        questionRef(eventId, questionId).update("upvoterUids",
                        upvote ? FieldValue.arrayUnion(uid) : FieldValue.arrayRemove(uid))
                .addOnCompleteListener(task -> finish(task.isSuccessful(), task.getException(),
                        "Only attendees who have checked in can upvote.", cb));
    }

    /** Organizer: marks a question as answered (or puts it back in the queue). */
    public static void setAnswered(String eventId, String questionId, boolean answered,
                                   Callback<Void> cb) {
        questionRef(eventId, questionId).update("answered", answered)
                .addOnCompleteListener(task -> finish(task.isSuccessful(), task.getException(),
                        "Only the organizers of this event can mark a question as answered.", cb));
    }

    /** The author can delete their own question; organizers can delete any. */
    public static void deleteQuestion(String eventId, String questionId, Callback<Void> cb) {
        questionRef(eventId, questionId).delete()
                .addOnCompleteListener(task -> finish(task.isSuccessful(), task.getException(),
                        "You can only delete your own questions.", cb));
    }

    // ------------------------------------------------------------------ helpers

    private static void finish(boolean ok, Exception error, String deniedMessage, Callback<Void> cb) {
        if (ok) {
            cb.onSuccess(null);
        } else {
            cb.onError(describe(error, deniedMessage));
        }
    }

    private static String describe(Exception e, String deniedMessage) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return deniedMessage;
            }
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "You need an internet connection for this.";
            }
        }
        if (e != null && e.getMessage() != null) {
            return e.getMessage();
        }
        return "Something went wrong. Please try again.";
    }
}

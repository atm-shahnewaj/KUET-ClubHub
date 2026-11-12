package com.kuet.clubhub.ui.live;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.CheckInRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.LiveRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityListBinding;
import com.kuet.clubhub.databinding.DialogAskQuestionBinding;
import com.kuet.clubhub.databinding.DialogPollFormBinding;
import com.kuet.clubhub.model.CheckIn;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.Poll;
import com.kuet.clubhub.model.Question;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.notify.ToastCenter;
import com.kuet.clubhub.util.QuestionRanker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The live part of an event, in two tabs:
 * <ul>
 *   <li><b>Polls</b>: organizers launch polls; checked-in students vote once; everybody sees the
 *       results as a bar chart that updates the moment a vote arrives.</li>
 *   <li><b>Q&amp;A</b>: checked-in students ask questions (optionally anonymously) and upvote the
 *       ones they care about; the most upvoted rise to the top; organizers mark them answered.</li>
 * </ul>
 * Everything is driven by Firestore real-time listeners that are started in onStart and removed in
 * onStop, so nothing is read while the screen is not visible.
 */
public class LiveActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";

    private static final int TAB_POLLS = 0;
    private static final int TAB_QUESTIONS = 1;

    private ActivityListBinding binding;
    private PollAdapter pollAdapter;
    private QuestionAdapter questionAdapter;
    private UserProfile profile;
    private String eventId;

    private Event event;
    private CheckIn myCheckIn;
    private List<Poll> polls = new ArrayList<>();
    private Map<String, Integer> myVotes = new HashMap<>();
    private List<Question> questions = new ArrayList<>();
    private boolean pollsLoaded = false;
    private boolean questionsLoaded = false;

    private int tab = TAB_POLLS;
    /** Which adapter the RecyclerView currently has, so it is swapped only when the tab changes. */
    private int attachedTab = -1;

    private ListenerRegistration eventReg;
    private ListenerRegistration checkInReg;
    private ListenerRegistration pollsReg;
    private ListenerRegistration votesReg;
    private ListenerRegistration questionsReg;

    private AlertDialog openDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        profile = new SessionManager(this).getProfile();
        if (eventId == null || profile == null) {
            finish();
            return;
        }
        binding.toolbar.setTitle(R.string.title_live);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        pollAdapter = new PollAdapter(new PollAdapter.Listener() {
            @Override
            public void onVote(Poll poll, int optionIndex) {
                confirmVote(poll, optionIndex);
            }

            @Override
            public void onToggleOpen(Poll poll) {
                final boolean reopen = !poll.acceptingVotes();
                LiveRepository.setPollOpen(eventId, poll.getId(), reopen, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        ToastCenter.showShort(LiveActivity.this,
                                reopen ? "Poll reopened for voting." : "Poll voting closed.");
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
            }

            @Override
            public void onDelete(Poll poll) {
                confirmDeletePoll(poll);
            }
        });
        questionAdapter = new QuestionAdapter(new QuestionAdapter.Listener() {
            @Override
            public void onUpvote(Question question, boolean upvote) {
                LiveRepository.setUpvote(eventId, question.getId(), profile.getUid(), upvote, quiet());
            }

            @Override
            public void onAnswered(Question question, boolean answered) {
                LiveRepository.setAnswered(eventId, question.getId(), answered, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        ToastCenter.showShort(LiveActivity.this,
                                answered ? "Question marked as answered." : "Question returned to queue.");
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
            }

            @Override
            public void onDelete(Question question) {
                confirmDeleteQuestion(question);
            }
        });

        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        // A live vote changes a card in place; the cross-fade of the default animation would
        // restart the bar animation, so changes are applied directly to the same card.
        RecyclerView.ItemAnimator animator = binding.recycler.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        binding.toggle.setVisibility(View.VISIBLE);
        binding.toggle.check(R.id.tab_1);
        binding.toggle.addOnButtonCheckedListener((group, id, isChecked) -> {
            if (isChecked) {
                tab = id == R.id.tab_1 ? TAB_POLLS : TAB_QUESTIONS;
                render();
            }
        });
        binding.btnA.setOnClickListener(v -> {
            if (tab == TAB_POLLS) {
                showPollDialog();
            } else {
                showAskDialog();
            }
        });
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventId == null || profile == null) {
            return;
        }
        eventReg = EventRepository.getInstance(this).listenEvent(eventId,
                new EventRepository.EventListener() {
                    @Override
                    public void onEvent(Event e) {
                        event = e;
                        render();
                    }

                    @Override
                    public void onMissing() {
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
        checkInReg = CheckInRepository.listenMyCheckIn(eventId, profile.getUid(),
                new CheckInRepository.MyCheckInListener() {
                    @Override
                    public void onChanged(CheckIn checkIn) {
                        myCheckIn = checkIn;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        // Organizers have no check-in; students see the guest hint instead.
                    }
                });
        pollsReg = LiveRepository.listenPolls(eventId, new LiveRepository.PollsListener() {
            @Override
            public void onPolls(List<Poll> list) {
                polls = list;
                pollsLoaded = true;
                render();
            }

            @Override
            public void onError(String message) {
                pollsLoaded = true;
                render();
                showError(message);
            }
        });
        votesReg = LiveRepository.listenMyVotes(eventId, profile.getUid(),
                new LiveRepository.MyVotesListener() {
                    @Override
                    public void onVotes(Map<String, Integer> votes) {
                        myVotes = votes;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        // Without this the buttons simply stay visible; a vote is refused by the rules anyway.
                    }
                });
        questionsReg = LiveRepository.listenQuestions(eventId, new LiveRepository.QuestionsListener() {
            @Override
            public void onQuestions(List<Question> list) {
                questions = list;
                questionsLoaded = true;
                render();
            }

            @Override
            public void onError(String message) {
                questionsLoaded = true;
                render();
                showError(message);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        eventReg = stop(eventReg);
        checkInReg = stop(checkInReg);
        pollsReg = stop(pollsReg);
        votesReg = stop(votesReg);
        questionsReg = stop(questionsReg);
        if (openDialog != null) {
            openDialog.dismiss();
            openDialog = null;
        }
    }

    private static ListenerRegistration stop(@Nullable ListenerRegistration r) {
        if (r != null) {
            r.remove();
        }
        return null;
    }

    // ------------------------------------------------------------------ who may do what

    private boolean isManager() {
        return profile.getRoleEnum() == Role.ADMIN || (event != null && event.hasOrganizer(profile.getUid()));
    }

    /** Voting and asking need verified attendance (a check-in record); the rules enforce the same. */
    private boolean isAttendee() {
        return !isManager() && myCheckIn != null;
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        if (binding == null) {
            return;
        }
        final boolean manager = isManager();
        final boolean attendee = isAttendee();

        binding.tvTitle.setText(event == null ? "" : event.getTitle());
        binding.tvSubtitle.setText(manager ? R.string.live_subtitle_manager
                : attendee ? R.string.live_subtitle_attendee : R.string.live_subtitle_guest);

        binding.tab1.setText(getString(R.string.tab_polls_count, polls.size()));
        binding.tab2.setText(getString(R.string.tab_questions_count, questions.size()));

        // The one action button: launch a poll (organizer) or ask a question (checked-in student).
        boolean showAction = tab == TAB_POLLS ? manager : attendee;
        binding.actions.setVisibility(showAction ? View.VISIBLE : View.GONE);
        binding.btnA.setVisibility(showAction ? View.VISIBLE : View.GONE);
        binding.btnA.setText(tab == TAB_POLLS ? R.string.action_launch_poll : R.string.action_ask_question);
        binding.btnB.setVisibility(View.GONE);

        if (attachedTab != tab) {
            binding.recycler.setAdapter(tab == TAB_POLLS ? pollAdapter : questionAdapter);
            attachedTab = tab;
        }

        boolean empty;
        boolean waiting;
        if (tab == TAB_POLLS) {
            List<PollAdapter.Item> items = pollItems(manager, attendee);
            pollAdapter.submitList(items);
            empty = items.isEmpty();
            waiting = !pollsLoaded && empty;
            binding.emptyState.setText(manager ? R.string.empty_polls_manager : R.string.empty_polls_student);
        } else {
            List<QuestionAdapter.Item> items = questionItems(manager, attendee);
            questionAdapter.submitList(items);
            empty = items.isEmpty();
            waiting = !questionsLoaded && empty;
            binding.emptyState.setText(attendee ? R.string.empty_questions_can_ask
                    : R.string.empty_questions_cannot_ask);
        }
        binding.progress.setVisibility(waiting ? View.VISIBLE : View.GONE);
        binding.emptyState.setVisibility(!waiting && empty ? View.VISIBLE : View.GONE);
    }

    /** Open polls first (newest on top), then closed ones. */
    private List<PollAdapter.Item> pollItems(boolean manager, boolean attendee) {
        List<Poll> sorted = new ArrayList<>(polls);
        Collections.sort(sorted, (a, b) -> {
            if (a.acceptingVotes() != b.acceptingVotes()) {
                return a.acceptingVotes() ? -1 : 1;
            }
            long ta = a.getCreatedAt() == null ? Long.MAX_VALUE : a.getCreatedAt().getTime();
            long tb = b.getCreatedAt() == null ? Long.MAX_VALUE : b.getCreatedAt().getTime();
            return Long.compare(tb, ta);
        });
        List<PollAdapter.Item> items = new ArrayList<>();
        for (Poll p : sorted) {
            Integer mine = myVotes.get(p.getId());
            items.add(new PollAdapter.Item(p, mine == null ? -1 : mine, attendee, manager));
        }
        return items;
    }

    private List<QuestionAdapter.Item> questionItems(boolean manager, boolean attendee) {
        List<QuestionAdapter.Item> items = new ArrayList<>();
        for (Question q : QuestionRanker.rank(questions)) {
            boolean mine = profile.getUid().equals(q.getAuthorUid());
            items.add(new QuestionAdapter.Item(q, q.upvotedBy(profile.getUid()), attendee,
                    mine || manager, manager));
        }
        return items;
    }

    // ------------------------------------------------------------------ polls

    private void confirmVote(Poll poll, int optionIndex) {
        String option = poll.getOptions().get(optionIndex);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_vote_title)
                .setMessage(getString(R.string.dialog_vote_message, option))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_vote, (d, w) ->
                        LiveRepository.vote(eventId, poll.getId(), profile.getUid(), optionIndex,
                                new Callback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        ToastCenter.showShort(LiveActivity.this, R.string.msg_vote_saved);
                                        Snackbar.make(binding.getRoot(), R.string.msg_vote_saved,
                                                Snackbar.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        showError(message);
                                    }
                                }))
                .show();
    }

    private void confirmDeletePoll(Poll poll) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_poll_title)
                .setMessage(R.string.dialog_delete_poll_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) ->
                        LiveRepository.deletePoll(eventId, poll.getId(), quiet()))
                .show();
    }

    /** The organizer's form: a question and two to four options. Validation keeps the dialog open. */
    private void showPollDialog() {
        final DialogPollFormBinding form = DialogPollFormBinding.inflate(getLayoutInflater());
        final TextView[] optionFields = {form.etPollOption1, form.etPollOption2,
                form.etPollOption3, form.etPollOption4};
        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_poll_title)
                .setView(form.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_launch_poll, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String question = form.etPollQuestion.getText() == null ? ""
                    : form.etPollQuestion.getText().toString().trim();
            form.tilPollQuestion.setError(null);
            form.tilPollOption1.setError(null);
            if (question.length() < 3) {
                form.tilPollQuestion.setError(getString(R.string.error_poll_question));
                return;
            }
            List<String> options = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (TextView field : optionFields) {
                String text = field.getText() == null ? "" : field.getText().toString().trim();
                if (!text.isEmpty() && seen.add(text.toLowerCase(Locale.ROOT))) {
                    options.add(text);
                }
            }
            if (options.size() < 2) {
                form.tilPollOption1.setError(getString(R.string.error_poll_options));
                return;
            }
            dialog.dismiss();
            LiveRepository.createPoll(eventId, profile, question, options, new Callback<String>() {
                @Override
                public void onSuccess(String pollId) {
                    String title = event == null ? "this event" : event.getTitle();
                    ToastCenter.livePollOrQaStarted(LiveActivity.this, title, question,
                            true, eventId, pollId);
                    Snackbar.make(binding.getRoot(), R.string.msg_poll_launched, Snackbar.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        }));
        openDialog = dialog;
        dialog.show();
    }

    // ------------------------------------------------------------------ questions

    private void showAskDialog() {
        final DialogAskQuestionBinding form = DialogAskQuestionBinding.inflate(getLayoutInflater());
        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_ask_title)
                .setView(form.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_post, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = form.etQuestion.getText() == null ? ""
                    : form.etQuestion.getText().toString().trim();
            if (text.length() < 5) {
                form.tilQuestion.setError(getString(R.string.error_question_short));
                return;
            }
            boolean anonymous = form.switchAnonymous.isChecked();
            dialog.dismiss();
            LiveRepository.ask(eventId, profile, text, anonymous, new Callback<String>() {
                @Override
                public void onSuccess(String questionId) {
                    ToastCenter.showShort(LiveActivity.this, R.string.msg_question_posted);
                    Snackbar.make(binding.getRoot(), R.string.msg_question_posted, Snackbar.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        }));
        openDialog = dialog;
        dialog.show();
    }

    private void confirmDeleteQuestion(Question question) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_question_title)
                .setMessage(R.string.dialog_delete_question_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) ->
                        LiveRepository.deleteQuestion(eventId, question.getId(), quiet()))
                .show();
    }

    // ------------------------------------------------------------------ helpers

    /** For actions whose result is visible in the list anyway: only a failure needs a message. */
    private Callback<Void> quiet() {
        return new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        };
    }

    private void showError(String message) {
        ToastCenter.show(this, message);
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}

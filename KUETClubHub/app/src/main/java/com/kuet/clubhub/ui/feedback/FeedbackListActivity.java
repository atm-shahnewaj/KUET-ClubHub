package com.kuet.clubhub.ui.feedback;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.FeedbackRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityListBinding;
import com.kuet.clubhub.model.FeedbackEntry;
import com.kuet.clubhub.ui.common.RowAdapter;
import com.kuet.clubhub.util.TimeFormats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Organizer screen: the average rating and every comment for one event. */
public class FeedbackListActivity extends BaseActivity implements RowAdapter.Listener {

    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_TITLE = "event_title";

    private ActivityListBinding binding;
    private RowAdapter adapter;
    private String eventId;
    private boolean loaded = false;
    private List<FeedbackEntry> entries = new ArrayList<>();
    private ListenerRegistration registration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || new SessionManager(this).getProfile() == null) {
            finish();
            return;
        }
        String title = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        binding.toolbar.setTitle(R.string.title_feedback);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.tvTitle.setText(title == null ? "" : title);

        adapter = new RowAdapter(this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventId == null) {
            return;
        }
        registration = FeedbackRepository.listenAll(eventId, new FeedbackRepository.FeedbackListener() {
            @Override
            public void onFeedback(List<FeedbackEntry> list) {
                entries = list;
                loaded = true;
                render();
            }

            @Override
            public void onError(String message) {
                loaded = true;
                render();
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private void render() {
        List<FeedbackEntry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, (a, b) -> {
            long ta = a.getUpdatedAt() == null ? 0 : a.getUpdatedAt().getTime();
            long tb = b.getUpdatedAt() == null ? 0 : b.getUpdatedAt().getTime();
            return Long.compare(tb, ta);
        });

        int sum = 0;
        for (FeedbackEntry e : sorted) {
            sum += e.getRating();
        }
        if (sorted.isEmpty()) {
            binding.tvSubtitle.setText(R.string.feedback_none_yet);
        } else {
            binding.tvSubtitle.setText(getString(R.string.feedback_summary,
                    String.format(Locale.getDefault(), "%.1f", sum / (double) sorted.size()),
                    sorted.size()));
        }

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (FeedbackEntry e : sorted) {
            String comment = e.getComment() == null ? "" : e.getComment().trim();
            String text = stars(e.getRating()) + (comment.isEmpty() ? "" : "\n" + comment);
            rows.add(RowAdapter.Row.item(e.getUid())
                    .avatar(RowAdapter.Row.initial(e.getName()))
                    .uid(e.getUid())
                    .title(e.getName())
                    .subtitle(text)
                    .pill(e.getUpdatedAt() == null ? "" : TimeFormats.monthShort(e.getUpdatedAt())
                            + " " + TimeFormats.dayNumber(e.getUpdatedAt()), RowAdapter.PILL_NEUTRAL));
        }
        adapter.submitList(rows);

        binding.progress.setVisibility(!loaded && rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.emptyState.setVisibility(loaded && rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.emptyState.setText(R.string.empty_feedback);
    }

    private static String stars(int rating) {
        int r = Math.max(0, Math.min(5, rating));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < r ? '★' : '☆');
        }
        return sb.toString();
    }

    @Override
    public void onRowClick(RowAdapter.Row row) {
    }

    @Override
    public void onPrimary(RowAdapter.Row row) {
    }

    @Override
    public void onSecondary(RowAdapter.Row row) {
    }
}

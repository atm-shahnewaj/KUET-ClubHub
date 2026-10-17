package com.kuet.clubhub.ui.events;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.databinding.ActivityEventParticipantsBinding;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Participant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Registered and waitlisted people for one event (organizers and admins). */
public class EventParticipantsActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_TITLE = "event_title";

    private ActivityEventParticipantsBinding binding;
    private ParticipantAdapter adapter;
    private String eventId;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventParticipantsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null) {
            finish();
            return;
        }
        String title = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        binding.tvEventTitle.setText(title == null ? "" : title);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ParticipantAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventId == null) {
            return;
        }
        registration = EventRepository.getInstance(this).listenParticipants(eventId,
                new EventRepository.ParticipantsListener() {
                    @Override
                    public void onParticipants(List<Participant> participants) {
                        show(participants);
                    }

                    @Override
                    public void onError(String message) {
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

    private void show(List<Participant> all) {
        List<Participant> registered = new ArrayList<>();
        List<Participant> waiting = new ArrayList<>();
        for (Participant p : all) {
            if (MyRegistration.REGISTERED.equals(p.getStatus())) {
                registered.add(p);
            } else if (MyRegistration.WAITLISTED.equals(p.getStatus())) {
                waiting.add(p);
            }
        }
        Collections.sort(registered, (a, b) -> lower(a.getName()).compareTo(lower(b.getName())));
        // The waiting list is shown in the order people joined.
        Collections.sort(waiting, (a, b) -> Long.compare(a.getWaitSeq(), b.getWaitSeq()));

        List<Participant> rows = new ArrayList<>(registered);
        rows.addAll(waiting);
        adapter.submitList(rows);

        binding.tvSummary.setText(getString(R.string.participants_summary,
                registered.size(), waiting.size()));
        binding.emptyState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}

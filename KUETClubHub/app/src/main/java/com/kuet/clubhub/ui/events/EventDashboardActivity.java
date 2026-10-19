package com.kuet.clubhub.ui.events;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.CheckInRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityEventDashboardBinding;
import com.kuet.clubhub.model.CheckIn;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Participant;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.scan.ScanActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The organizer dashboard for one event: registrations, waiting list, seats and a live
 * attendance counter that updates as people check in.
 */
public class EventDashboardActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_TITLE = "event_title";

    private ActivityEventDashboardBinding binding;
    private ParticipantAdapter adapter;
    private EventRepository events;
    private UserProfile profile;
    private String eventId;
    private String eventTitle = "";

    private Event event;
    private List<Participant> participants = new ArrayList<>();
    private Map<String, CheckIn> checkIns = new HashMap<>();

    private ListenerRegistration eventRegistration;
    private ListenerRegistration participantsRegistration;
    private ListenerRegistration checkInsRegistration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        profile = new SessionManager(this).getProfile();
        if (eventId == null || profile == null) {
            finish();
            return;
        }
        String title = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        eventTitle = title == null ? "" : title;
        binding.tvEventTitle.setText(eventTitle);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        events = EventRepository.getInstance(this);

        adapter = new ParticipantAdapter(this::onParticipantClicked);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.btnOpenCheckin.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventCheckInActivity.class);
            intent.putExtra(EventCheckInActivity.EXTRA_EVENT_ID, eventId);
            intent.putExtra(EventCheckInActivity.EXTRA_EVENT_TITLE, eventTitle);
            startActivity(intent);
        });
        binding.btnScanPasses.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_ORGANIZER_SCAN);
            intent.putExtra(ScanActivity.EXTRA_EVENT_ID, eventId);
            intent.putExtra(ScanActivity.EXTRA_EVENT_TITLE, eventTitle);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventId == null || profile == null) {
            return;
        }
        eventRegistration = events.listenEvent(eventId, new EventRepository.EventListener() {
            @Override
            public void onEvent(Event e) {
                event = e;
                eventTitle = e.getTitle() == null ? "" : e.getTitle();
                binding.tvEventTitle.setText(eventTitle);
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
        participantsRegistration = events.listenParticipants(eventId,
                new EventRepository.ParticipantsListener() {
                    @Override
                    public void onParticipants(List<Participant> list) {
                        participants = list;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
        checkInsRegistration = CheckInRepository.listenCheckIns(eventId,
                new CheckInRepository.CheckInsListener() {
                    @Override
                    public void onChanged(List<CheckIn> list) {
                        Map<String, CheckIn> map = new HashMap<>();
                        for (CheckIn c : list) {
                            map.put(c.getUid(), c);
                        }
                        checkIns = map;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventRegistration != null) {
            eventRegistration.remove();
            eventRegistration = null;
        }
        if (participantsRegistration != null) {
            participantsRegistration.remove();
            participantsRegistration = null;
        }
        if (checkInsRegistration != null) {
            checkInsRegistration.remove();
            checkInsRegistration = null;
        }
    }

    private void render() {
        List<Participant> registered = new ArrayList<>();
        List<Participant> waiting = new ArrayList<>();
        for (Participant p : participants) {
            CheckIn c = checkIns.get(p.getUid());
            p.setCheckedIn(c != null);
            p.setCheckedInAt(c == null ? null : c.getTimestamp());
            if (MyRegistration.REGISTERED.equals(p.getStatus())) {
                registered.add(p);
            } else if (MyRegistration.WAITLISTED.equals(p.getStatus())) {
                waiting.add(p);
            }
        }
        Collections.sort(registered, (a, b) -> lower(a.getName()).compareTo(lower(b.getName())));
        Collections.sort(waiting, (a, b) -> Long.compare(a.getWaitSeq(), b.getWaitSeq()));

        int present = 0;
        for (Participant p : registered) {
            if (p.isCheckedIn()) {
                present++;
            }
        }

        binding.tvBigCount.setText(String.valueOf(present));
        binding.tvBigLabel.setText(getString(R.string.dashboard_checked_in_of, registered.size()));
        binding.tvStatRegistered.setText(String.valueOf(registered.size()));
        binding.tvStatWaiting.setText(String.valueOf(waiting.size()));
        binding.tvStatMissing.setText(String.valueOf(registered.size() - present));
        binding.tvStatSeats.setText(event == null ? "-"
                : String.valueOf(event.getCapacity()));

        List<Participant> rows = new ArrayList<>(registered);
        rows.addAll(waiting);
        adapter.submitList(rows);
        binding.emptyState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Tapping a registered student who has not arrived offers a manual check-in. */
    private void onParticipantClicked(Participant p) {
        if (!MyRegistration.REGISTERED.equals(p.getStatus()) || p.isCheckedIn()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_manual_title)
                .setMessage(getString(R.string.dialog_manual_message, p.getName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_check_in, (d, w) ->
                        CheckInRepository.manualCheckIn(eventId, p.getUid(), profile.getUid(),
                                new Callback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        Toast.makeText(EventDashboardActivity.this,
                                                getString(R.string.msg_manual_done, p.getName()),
                                                Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        showError(message);
                                    }
                                }))
                .show();
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}

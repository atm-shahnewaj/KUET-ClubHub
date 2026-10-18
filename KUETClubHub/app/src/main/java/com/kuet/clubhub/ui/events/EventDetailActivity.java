package com.kuet.clubhub.ui.events;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityEventDetailBinding;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.EventType;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.provider.ScheduleClashChecker;
import com.kuet.clubhub.util.EventTypeStyle;
import com.kuet.clubhub.util.TimeFormats;

import java.util.Date;

public class EventDetailActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";

    private ActivityEventDetailBinding binding;
    private EventRepository repository;
    private UserProfile profile;
    private String eventId;

    private Event current;
    private MyRegistration myReg;
    private boolean busy = false;
    private ScheduleClashChecker.Clash clash;
    private boolean clashChecking = false;

    private ListenerRegistration eventRegistration;
    private ListenerRegistration myRegRegistration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        profile = new SessionManager(this).getProfile();
        if (eventId == null || profile == null) {
            finish();
            return;
        }
        repository = EventRepository.getInstance(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnRegister.setOnClickListener(v -> onRegisterClicked());
        binding.btnLeave.setOnClickListener(v -> confirmLeave());
        binding.btnParticipants.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventParticipantsActivity.class);
            intent.putExtra(EventParticipantsActivity.EXTRA_EVENT_ID, eventId);
            intent.putExtra(EventParticipantsActivity.EXTRA_EVENT_TITLE,
                    current == null ? "" : current.getTitle());
            startActivity(intent);
        });
        binding.btnEditEvent.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventEditActivity.class);
            intent.putExtra(EventEditActivity.EXTRA_EVENT_ID, eventId);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventId == null || profile == null) {
            return;
        }
        eventRegistration = repository.listenEvent(eventId, new EventRepository.EventListener() {
            @Override
            public void onEvent(Event event) {
                current = event;
                render();
            }

            @Override
            public void onMissing() {
                Toast.makeText(EventDetailActivity.this,
                        R.string.msg_event_missing, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
        if (profile.getRoleEnum() != Role.ADMIN) {
            myRegRegistration = repository.listenMyRegistration(profile.getUid(), eventId,
                    new EventRepository.MyRegistrationListener() {
                        @Override
                        public void onChanged(MyRegistration reg) {
                            myReg = reg;
                            render();
                        }

                        @Override
                        public void onError(String message) {
                            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                        }
                    });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventRegistration != null) {
            eventRegistration.remove();
            eventRegistration = null;
        }
        if (myRegRegistration != null) {
            myRegRegistration.remove();
            myRegRegistration = null;
        }
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        if (current == null) {
            return;
        }
        Event e = current;
        EventType type = e.typeEnum();

        binding.tvTitle.setText(e.getTitle());
        binding.tvClub.setText(e.getClubName());
        EventTypeStyle.apply(binding.tvType, type);

        binding.tvDate.setText(TimeFormats.fullDate(e.getStartTime()));
        binding.tvTime.setText(TimeFormats.timeRange(e.getStartTime(), e.getEndTime()));
        binding.tvVenue.setText(e.getVenue());
        binding.tvSeats.setText(getString(R.string.seats_taken,
                (int) e.getRegisteredCount(), (int) e.getCapacity()));
        binding.rowWaitlist.setVisibility(e.getWaitlistCount() > 0 ? View.VISIBLE : View.GONE);
        binding.tvWaitlist.setText(String.valueOf(e.getWaitlistCount()));

        binding.tvDescription.setText(e.getDescription());
        if (e.getTags() != null && !e.getTags().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String tag : e.getTags()) {
                sb.append('#').append(tag).append("  ");
            }
            binding.tvTags.setText(sb.toString().trim());
            binding.tvTags.setVisibility(View.VISIBLE);
        } else {
            binding.tvTags.setVisibility(View.GONE);
        }

        renderCompetition(e);
        renderRegistration(e);
        checkClash(e);

        boolean manager = profile.getRoleEnum() == Role.ADMIN || e.hasOrganizer(profile.getUid());
        binding.managerBlock.setVisibility(manager ? View.VISIBLE : View.GONE);
    }

    private void renderCompetition(Event e) {
        boolean competition = e.typeEnum() == EventType.COMPETITION;
        binding.cardCompetition.setVisibility(competition ? View.VISIBLE : View.GONE);
        if (!competition) {
            return;
        }
        binding.tvDeadline.setText(e.getRegistrationDeadline() == null
                ? getString(R.string.no_deadline) : TimeFormats.dateTime(e.getRegistrationDeadline()));
        binding.tvTeamSize.setText(e.getTeamSizeMax() <= 0 ? "-"
                : e.getTeamSizeMin() == e.getTeamSizeMax()
                ? String.valueOf(e.getTeamSizeMax())
                : e.getTeamSizeMin() + " – " + e.getTeamSizeMax());
        setOptionalRow(binding.rowPrizes, binding.tvPrizes, e.getPrizes());
        setOptionalRow(binding.rowRules, binding.tvRules, e.getRules());
    }

    private void setOptionalRow(View row, android.widget.TextView value, String text) {
        boolean has = text != null && !text.trim().isEmpty();
        row.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            value.setText(text);
        }
    }

    private void renderRegistration(Event e) {
        binding.btnRegister.setVisibility(View.GONE);
        binding.btnLeave.setVisibility(View.GONE);
        binding.tvMyStatus.setVisibility(View.GONE);
        binding.tvClosed.setVisibility(View.GONE);

        if (profile.getRoleEnum() == Role.ADMIN) {
            return; // admins moderate; they do not register
        }
        long now = System.currentTimeMillis();
        String status = myReg != null && myReg.isActive() ? myReg.getStatus() : null;

        if (MyRegistration.REGISTERED.equals(status)) {
            binding.tvMyStatus.setVisibility(View.VISIBLE);
            binding.tvMyStatus.setText(R.string.my_status_registered);
            if (!e.hasEnded(now)) {
                binding.btnLeave.setVisibility(View.VISIBLE);
                binding.btnLeave.setText(R.string.action_cancel_registration);
            }
        } else if (MyRegistration.WAITLISTED.equals(status)) {
            long position = Math.max(1, myReg.getWaitSeq() - e.getWaitHead() + 1);
            binding.tvMyStatus.setVisibility(View.VISIBLE);
            binding.tvMyStatus.setText(getString(R.string.my_status_waitlisted, (int) position));
            binding.btnLeave.setVisibility(View.VISIBLE);
            binding.btnLeave.setText(R.string.action_leave_waitlist);
        } else if (e.registrationClosed(now)) {
            binding.tvClosed.setVisibility(View.VISIBLE);
        } else {
            binding.btnRegister.setVisibility(View.VISIBLE);
            binding.btnRegister.setText(e.seatsLeft() > 0
                    ? R.string.action_register_event : R.string.action_join_waitlist);
        }
        binding.btnRegister.setEnabled(!busy);
        binding.btnLeave.setEnabled(!busy);
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.progress.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!value);
        binding.btnLeave.setEnabled(!value);
    }

    // ------------------------------------------------------------------ actions

    /** Warns first if this event overlaps one the student is already registered for. */
    private void onRegisterClicked() {
        if (clash == null) {
            register();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_clash_title)
                .setMessage(getString(R.string.dialog_clash_message, clash.title))
                .setNegativeButton(R.string.action_back_out, null)
                .setPositiveButton(R.string.action_register_anyway, (d, w) -> register())
                .show();
    }

    /**
     * Asks the ClubHubProvider (through a ContentResolver) whether this event overlaps a
     * registered one, and shows a warning if so.
     */
    private void checkClash(Event e) {
        String status = myReg != null && myReg.isActive() ? myReg.getStatus() : null;
        if (profile.getRoleEnum() == Role.ADMIN || status != null
                || e.registrationClosed(System.currentTimeMillis())) {
            clash = null;
            showClash();
            return;
        }
        if (clashChecking) {
            return;
        }
        clashChecking = true;
        ScheduleClashChecker.check(this, e, result -> {
            clashChecking = false;
            clash = result;
            showClash();
        });
    }

    private void showClash() {
        if (clash == null) {
            binding.tvClash.setVisibility(View.GONE);
            return;
        }
        binding.tvClash.setText(getString(R.string.clash_warning, clash.title,
                TimeFormats.timeRange(new Date(clash.startMillis), new Date(clash.endMillis))));
        binding.tvClash.setVisibility(View.VISIBLE);
    }

    private void register() {
        setBusy(true);
        repository.register(eventId, profile, new Callback<String>() {
            @Override
            public void onSuccess(String status) {
                setBusy(false);
                Snackbar.make(binding.getRoot(),
                        MyRegistration.WAITLISTED.equals(status)
                                ? R.string.msg_waitlisted : R.string.msg_registered,
                        Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void confirmLeave() {
        boolean waiting = myReg != null && MyRegistration.WAITLISTED.equals(myReg.getStatus());
        new MaterialAlertDialogBuilder(this)
                .setTitle(waiting ? R.string.dialog_leave_waitlist_title
                        : R.string.dialog_cancel_registration_title)
                .setMessage(waiting ? R.string.dialog_leave_waitlist_message
                        : R.string.dialog_cancel_registration_message)
                .setNegativeButton(R.string.action_keep, null)
                .setPositiveButton(R.string.action_confirm, (d, w) -> cancel())
                .show();
    }

    private void cancel() {
        setBusy(true);
        repository.cancelRegistration(eventId, profile.getUid(), new Callback<String>() {
            @Override
            public void onSuccess(String result) {
                setBusy(false);
                Snackbar.make(binding.getRoot(), R.string.msg_cancelled, Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}

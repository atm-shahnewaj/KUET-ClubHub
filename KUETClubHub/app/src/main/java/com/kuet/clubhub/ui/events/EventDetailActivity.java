package com.kuet.clubhub.ui.events;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.CheckInRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.FeedbackRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.WeatherRepository;
import com.kuet.clubhub.databinding.ActivityEventDetailBinding;
import com.kuet.clubhub.databinding.DialogFeedbackBinding;
import com.kuet.clubhub.model.CheckIn;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.EventType;
import com.kuet.clubhub.model.FeedbackEntry;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.model.WeatherForecast;
import com.kuet.clubhub.notify.ToastCenter;
import com.kuet.clubhub.provider.ScheduleClashChecker;
import com.kuet.clubhub.ui.certificate.CertificateActivity;
import com.kuet.clubhub.ui.clubs.ClubDetailActivity;
import com.kuet.clubhub.ui.feedback.FeedbackListActivity;
import com.kuet.clubhub.ui.live.LiveActivity;
import com.kuet.clubhub.ui.scan.ScanActivity;
import com.kuet.clubhub.ui.teams.TeamsActivity;
import com.kuet.clubhub.util.CalendarHelper;
import com.kuet.clubhub.util.CountdownText;
import com.kuet.clubhub.util.EventTypeStyle;
import com.kuet.clubhub.util.TimeFormats;
import com.kuet.clubhub.util.WeatherParser;

import java.util.Date;

public class EventDetailActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";

    private ActivityEventDetailBinding binding;
    private EventRepository repository;
    private UserProfile profile;
    private String eventId;

    private Event current;
    private MyRegistration myReg;
    private CheckIn myCheckIn;
    private FeedbackEntry myFeedback;
    private boolean busy = false;
    private ScheduleClashChecker.Clash clash;
    private boolean clashChecking = false;

    /** Tracks whether the event was still ongoing on the previous tick so we can toast on completion. */
    private Boolean wasEndedPreviously = null;
    private boolean promptedFeedbackAndCertThisVisit = false;

    /** Tracks which event date the weather forecast has already been requested for. */
    private String weatherRequestedIso = "";

    private ListenerRegistration eventRegistration;
    private ListenerRegistration myRegRegistration;
    private ListenerRegistration myCheckInRegistration;
    private ListenerRegistration myFeedbackRegistration;

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTicker = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            if (current != null && !current.hasEnded(System.currentTimeMillis())) {
                countdownHandler.postDelayed(this, 1_000L);
            }
        }
    };

    /** The student scans the organizer's rotating code; the result is shown by the scanner itself. */
    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    ToastCenter.show(this, R.string.msg_checked_in);
                    Snackbar.make(binding.getRoot(), R.string.msg_checked_in, Snackbar.LENGTH_LONG).show();
                }
            });

    /**
     * Requests READ_CALENDAR and WRITE_CALENDAR for direct CalendarContract insertion; if denied,
     * CalendarHelper degrades gracefully by opening the calendar app's pre-filled insert screen.
     */
    private final ActivityResultLauncher<String[]> calendarPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> performAddToCalendar());

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
        binding.cardHostClub.setOnClickListener(v -> {
            if (current != null && current.getClubId() != null && !current.getClubId().isEmpty()) {
                Intent i = new Intent(this, ClubDetailActivity.class);
                i.putExtra(ClubDetailActivity.EXTRA_CLUB_ID, current.getClubId());
                startActivity(i);
            }
        });
        binding.btnShareEvent.setOnClickListener(v -> shareEvent());

        binding.btnRegister.setOnClickListener(v -> onRegisterClicked());
        binding.btnLeave.setOnClickListener(v -> confirmLeave());
        binding.btnParticipants.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventDashboardActivity.class);
            intent.putExtra(EventDashboardActivity.EXTRA_EVENT_ID, eventId);
            intent.putExtra(EventDashboardActivity.EXTRA_EVENT_TITLE,
                    current == null ? "" : current.getTitle());
            startActivity(intent);
        });
        binding.btnAnalytics.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventAnalyticsActivity.class);
            intent.putExtra(EventAnalyticsActivity.EXTRA_EVENT_ID, eventId);
            intent.putExtra(EventAnalyticsActivity.EXTRA_EVENT_TITLE,
                    current == null ? "" : current.getTitle());
            startActivity(intent);
        });
        binding.btnShowPass.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventPassActivity.class);
            intent.putExtra(EventPassActivity.EXTRA_EVENT_ID, eventId);
            startActivity(intent);
        });
        binding.btnAddCalendar.setOnClickListener(v -> onAddCalendarClicked());
        binding.btnWeatherRetry.setOnClickListener(v -> {
            if (current != null) {
                loadWeather(current, true);
            }
        });
        binding.btnScanCheckin.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_STUDENT_CHECKIN);
            intent.putExtra(ScanActivity.EXTRA_EVENT_ID, eventId);
            intent.putExtra(ScanActivity.EXTRA_EVENT_TITLE, current == null ? "" : current.getTitle());
            scanLauncher.launch(intent);
        });
        binding.btnTeams.setOnClickListener(v -> {
            Intent intent = new Intent(this, TeamsActivity.class);
            intent.putExtra(TeamsActivity.EXTRA_EVENT_ID, eventId);
            startActivity(intent);
        });
        // Polls and Q&A: the same screen for students and organizers (it adapts to the role).
        View.OnClickListener openLive = v -> {
            Intent intent = new Intent(this, LiveActivity.class);
            intent.putExtra(LiveActivity.EXTRA_EVENT_ID, eventId);
            startActivity(intent);
        };
        binding.btnLive.setOnClickListener(openLive);
        binding.btnManageLive.setOnClickListener(openLive);
        binding.btnFeedback.setOnClickListener(v -> showFeedbackDialog());
        binding.btnCertificate.setOnClickListener(v -> {
            Intent intent = new Intent(this, CertificateActivity.class);
            intent.putExtra(CertificateActivity.EXTRA_EVENT_ID, eventId);
            startActivity(intent);
        });
        binding.btnViewFeedback.setOnClickListener(v -> {
            Intent intent = new Intent(this, FeedbackListActivity.class);
            intent.putExtra(FeedbackListActivity.EXTRA_EVENT_ID, eventId);
            intent.putExtra(FeedbackListActivity.EXTRA_EVENT_TITLE,
                    current == null ? "" : current.getTitle());
            startActivity(intent);
        });
        binding.btnCloneEvent.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventEditActivity.class);
            intent.putExtra(EventEditActivity.EXTRA_CLONE_FROM_ID, eventId);
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
                            String prevStatus = myReg == null ? null : myReg.getStatus();
                            String nextStatus = reg == null ? null : reg.getStatus();
                            myReg = reg;
                            if (MyRegistration.WAITLISTED.equals(prevStatus)
                                    && MyRegistration.REGISTERED.equals(nextStatus)) {
                                String title = current != null ? current.getTitle() : reg.getTitle();
                                ToastCenter.waitlistPromoted(EventDetailActivity.this, title, eventId);
                            }
                            render();
                        }

                        @Override
                        public void onError(String message) {
                            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                        }
                    });
            myCheckInRegistration = CheckInRepository.listenMyCheckIn(eventId, profile.getUid(),
                    new CheckInRepository.MyCheckInListener() {
                        @Override
                        public void onChanged(CheckIn checkIn) {
                            myCheckIn = checkIn;
                            render();
                        }

                        @Override
                        public void onError(String message) {
                            // The status pill is a nicety; ignore (for example while offline).
                        }
                    });
            myFeedbackRegistration = FeedbackRepository.listenMine(eventId, profile.getUid(),
                    new FeedbackRepository.MyFeedbackListener() {
                        @Override
                        public void onChanged(FeedbackEntry entry) {
                            myFeedback = entry;
                            render();
                        }

                        @Override
                        public void onError(String message) {
                            // Rating is optional; ignore (for example while offline).
                        }
                    });
        }
        startCountdownTicker();
    }

    @Override
    protected void onStop() {
        super.onStop();
        countdownHandler.removeCallbacks(countdownTicker);
        if (eventRegistration != null) {
            eventRegistration.remove();
            eventRegistration = null;
        }
        if (myRegRegistration != null) {
            myRegRegistration.remove();
            myRegRegistration = null;
        }
        if (myCheckInRegistration != null) {
            myCheckInRegistration.remove();
            myCheckInRegistration = null;
        }
        if (myFeedbackRegistration != null) {
            myFeedbackRegistration.remove();
            myFeedbackRegistration = null;
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
        binding.tvClub.setText(e.getClubName() == null || e.getClubName().isEmpty()
                ? "KUET Campus Club" : e.getClubName());
        EventTypeStyle.apply(binding.tvType, type);
        com.kuet.clubhub.util.ImageHelper.bindEventBanner(binding.ivEventBanner, e.getBannerBase64(), type);

        binding.tvDate.setText(TimeFormats.fullDate(e.getStartTime()));
        binding.tvTime.setText(TimeFormats.timeRange(e.getStartTime(), e.getEndTime()));
        binding.tvVenue.setText(e.getVenue());

        int regCount = (int) e.getRegisteredCount();
        int cap = (int) e.getCapacity();
        binding.tvSeats.setText(getString(R.string.seats_taken, regCount, cap));
        int fillPct = cap > 0 ? Math.min(100, Math.round(regCount * 100f / cap)) : 0;
        binding.progressSeats.setProgress(fillPct);

        if (e.hasEnded(System.currentTimeMillis())) {
            binding.tvCapacityTile.setText(R.string.event_ended);
        } else if (cap > 0 && e.seatsLeft() == 0) {
            binding.tvCapacityTile.setText(getString(R.string.seats_full_waitlist, (int) e.getWaitlistCount()));
        } else {
            binding.tvCapacityTile.setText(getString(R.string.seats_left, (int) e.seatsLeft(), cap));
        }

        binding.rowWaitlist.setVisibility(e.getWaitlistCount() > 0 ? View.VISIBLE : View.GONE);
        binding.tvWaitlist.setText(String.valueOf(e.getWaitlistCount()));

        startCountdownTicker();
        renderWeather(e);

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

    private void startCountdownTicker() {
        countdownHandler.removeCallbacks(countdownTicker);
        updateCountdown();
        if (current != null && !current.hasEnded(System.currentTimeMillis())) {
            countdownHandler.postDelayed(countdownTicker, 1_000L);
        }
    }

    private void updateCountdown() {
        if (binding == null || current == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean endedNow = current.hasEnded(now);
        binding.tvCountdown.setText(CountdownText.formatDetail(
                current.getStartTime(), current.getEndTime(), now));

        if (wasEndedPreviously != null && !wasEndedPreviously && endedNow) {
            wasEndedPreviously = true;
            renderRegistration(current);
            if (myCheckIn != null) {
                ToastCenter.certificateReady(this, current.getTitle(), eventId);
                if (myFeedback == null) {
                    countdownHandler.postDelayed(() ->
                            ToastCenter.feedbackPrompt(EventDetailActivity.this,
                                    current.getTitle(), eventId), 2_500L);
                }
            }
        } else {
            wasEndedPreviously = endedNow;
        }
    }

    private void renderWeather(Event e) {
        if (e.getStartTime() == null || e.hasEnded(System.currentTimeMillis())) {
            binding.cardWeather.setVisibility(View.GONE);
            return;
        }
        binding.cardWeather.setVisibility(View.VISIBLE);
        String iso = WeatherParser.isoDate(e.getStartTime());
        if (!iso.equals(weatherRequestedIso)) {
            weatherRequestedIso = iso;
            loadWeather(e, false);
        }
    }

    private void loadWeather(Event e, boolean forceRefresh) {
        binding.weatherLoading.setVisibility(View.VISIBLE);
        binding.weatherContent.setVisibility(View.GONE);
        binding.weatherError.setVisibility(View.GONE);
        binding.tvWeatherBadge.setVisibility(View.GONE);

        WeatherRepository.fetchForDate(e.getStartTime(), forceRefresh,
                new Callback<WeatherForecast>() {
                    @Override
                    public void onSuccess(WeatherForecast f) {
                        if (isDestroyed() || binding == null) {
                            return;
                        }
                        binding.weatherLoading.setVisibility(View.GONE);
                        binding.weatherError.setVisibility(View.GONE);
                        binding.weatherContent.setVisibility(View.VISIBLE);
                        binding.tvWeatherBadge.setVisibility(View.VISIBLE);
                        binding.tvWeatherBadge.setText(getString(R.string.weather_temp_badge,
                                f.getTempMinC(), f.getTempMaxC()));
                        String dateLabel = f.getDate();
                        try {
                            java.util.Date d = new java.text.SimpleDateFormat(
                                    "yyyy-MM-dd", java.util.Locale.ENGLISH).parse(f.getDate());
                            if (d != null) {
                                dateLabel = new java.text.SimpleDateFormat(
                                        "EEEE, d MMMM yyyy", java.util.Locale.ENGLISH).format(d);
                            }
                        } catch (java.text.ParseException ignored) {
                            // keep the raw yyyy-MM-dd text
                        }
                        binding.tvWeatherSummary.setText(getString(R.string.weather_summary_line,
                                f.getCondition(), f.getTempMaxC(), f.getTempMinC()));
                        String detail = getString(f.isExactEventDate()
                                        ? R.string.weather_for_date_event
                                        : R.string.weather_for_date_today, dateLabel)
                                + "\n" + getString(R.string.weather_detail_line,
                                f.getRainChancePercent(), f.getWindMaxKmh());
                        if (!f.isExactEventDate()) {
                            detail = detail + "\n" + getString(R.string.weather_beyond_horizon_note);
                        }
                        binding.tvWeatherDetail.setText(detail);
                    }

                    @Override
                    public void onError(String message) {
                        if (isDestroyed() || binding == null) {
                            return;
                        }
                        binding.weatherLoading.setVisibility(View.GONE);
                        binding.weatherContent.setVisibility(View.GONE);
                        binding.weatherError.setVisibility(View.VISIBLE);
                        binding.tvWeatherError.setText(message);
                    }
                });
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
        binding.tvCheckedIn.setVisibility(View.GONE);
        binding.btnShowPass.setVisibility(View.GONE);
        binding.btnAddCalendar.setVisibility(View.GONE);
        binding.btnScanCheckin.setVisibility(View.GONE);
        binding.btnTeams.setVisibility(View.GONE);
        binding.btnFeedback.setVisibility(View.GONE);
        binding.btnLive.setVisibility(View.GONE);
        binding.btnCertificate.setVisibility(View.GONE);

        long now = System.currentTimeMillis();
        if (!e.hasEnded(now)) {
            binding.btnAddCalendar.setVisibility(View.VISIBLE);
        }

        if (profile.getRoleEnum() != Role.ADMIN) {
            String status = myReg != null && myReg.isActive() ? myReg.getStatus() : null;

            if (MyRegistration.REGISTERED.equals(status)) {
                binding.tvMyStatus.setVisibility(View.VISIBLE);
                binding.tvMyStatus.setText(R.string.my_status_registered);
                // Polls and Q&A are open to every registered student (voting itself needs a check-in).
                binding.btnLive.setVisibility(View.VISIBLE);
                if (e.typeEnum() == EventType.COMPETITION && e.getTeamSizeMax() > 0 && !e.hasEnded(now)) {
                    binding.btnTeams.setVisibility(View.VISIBLE);
                }
                if (myCheckIn != null) {
                    // Present: the pass and the cancel button are no longer needed.
                    binding.tvCheckedIn.setVisibility(View.VISIBLE);
                    // Attendance is verified, so rating and (after the event) the certificate unlock.
                    binding.btnFeedback.setVisibility(View.VISIBLE);
                    binding.btnFeedback.setText(myFeedback == null ? getString(R.string.action_rate_event)
                            : getString(R.string.action_edit_rating, myFeedback.getRating()));
                    if (e.hasEnded(now)) {
                        binding.btnCertificate.setVisibility(View.VISIBLE);
                        if (!promptedFeedbackAndCertThisVisit && myFeedback == null) {
                            promptedFeedbackAndCertThisVisit = true;
                            ToastCenter.feedbackPrompt(this, e.getTitle(), eventId);
                        }
                    }
                } else if (!e.hasEnded(now)) {
                    binding.btnShowPass.setVisibility(View.VISIBLE);
                    binding.btnScanCheckin.setVisibility(View.VISIBLE);
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

        boolean anyAttendeeActionVisible =
                binding.btnScanCheckin.getVisibility() == View.VISIBLE
                        || binding.btnShowPass.getVisibility() == View.VISIBLE
                        || binding.btnLive.getVisibility() == View.VISIBLE
                        || binding.btnTeams.getVisibility() == View.VISIBLE
                        || binding.btnAddCalendar.getVisibility() == View.VISIBLE
                        || binding.btnFeedback.getVisibility() == View.VISIBLE
                        || binding.btnCertificate.getVisibility() == View.VISIBLE;
        binding.cardAttendeeActions.setVisibility(anyAttendeeActionVisible ? View.VISIBLE : View.GONE);
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.progress.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!value);
        binding.btnLeave.setEnabled(!value);
    }

    // ------------------------------------------------------------------ actions

    private void shareEvent() {
        if (current == null) return;
        String text = current.getTitle() + " (" + current.typeEnum().getLabel() + ")\n"
                + "Hosted by: " + (current.getClubName() == null ? "KUET Club" : current.getClubName()) + "\n"
                + "When: " + TimeFormats.fullDate(current.getStartTime()) + " · "
                + TimeFormats.timeRange(current.getStartTime(), current.getEndTime()) + "\n"
                + "Venue: " + (current.getVenue() == null ? "KUET Campus" : current.getVenue());
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, current.getTitle());
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "Share Event"));
    }

    private void onAddCalendarClicked() {
        if (current == null || current.getStartTime() == null) {
            return;
        }
        performAddToCalendar();
    }

    private void performAddToCalendar() {
        if (current == null) {
            return;
        }
        int result = CalendarHelper.addOrOpenCalendar(this, current);
        if (result == CalendarHelper.RESULT_INSERTED_DIRECTLY) {
            ToastCenter.showShort(this, R.string.msg_calendar_added);
            Snackbar.make(binding.getRoot(), R.string.msg_calendar_added, Snackbar.LENGTH_LONG).show();
        } else if (result == CalendarHelper.RESULT_ALREADY_EXISTS) {
            ToastCenter.showShort(this, R.string.msg_calendar_already_added);
            Snackbar.make(binding.getRoot(), R.string.msg_calendar_already_added,
                    Snackbar.LENGTH_LONG).show();
        } else if (result == CalendarHelper.RESULT_NO_CALENDAR_APP) {
            ToastCenter.showShort(this, R.string.msg_no_calendar_app);
            Snackbar.make(binding.getRoot(), R.string.msg_no_calendar_app, Snackbar.LENGTH_LONG).show();
        }
    }

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

    /** A 1 to 5 star rating and an optional comment; the rules accept it only after check-in. */
    private void showFeedbackDialog() {
        final DialogFeedbackBinding form = DialogFeedbackBinding.inflate(getLayoutInflater());
        if (myFeedback != null) {
            form.ratingBar.setRating(myFeedback.getRating());
            form.etComment.setText(myFeedback.getComment());
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_feedback_title)
                .setView(form.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int rating = Math.round(form.ratingBar.getRating());
                    if (rating < 1) {
                        Toast.makeText(this, R.string.error_rating_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String comment = form.etComment.getText() == null ? ""
                            : form.etComment.getText().toString().trim();
                    dialog.dismiss();
                    FeedbackRepository.save(eventId, profile, rating, comment, new Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            ToastCenter.showShort(EventDetailActivity.this, R.string.msg_feedback_saved);
                            Snackbar.make(binding.getRoot(), R.string.msg_feedback_saved,
                                    Snackbar.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String message) {
                            ToastCenter.show(EventDetailActivity.this, message);
                            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                        }
                    });
                }));
        dialog.show();
    }

    private void register() {
        setBusy(true);
        repository.register(eventId, profile, new Callback<String>() {
            @Override
            public void onSuccess(String status) {
                setBusy(false);
                boolean waitlisted = MyRegistration.WAITLISTED.equals(status);
                String title = current == null ? "this event" : current.getTitle();
                String passCode = myReg != null ? myReg.getPassCode() : null;
                ToastCenter.registrationConfirmed(EventDetailActivity.this, title, passCode, waitlisted);
                Snackbar spacer = Snackbar.make(binding.getRoot(),
                        waitlisted ? R.string.msg_waitlisted : R.string.msg_registered,
                        Snackbar.LENGTH_LONG);
                if (!waitlisted) {
                    spacer.setAction(R.string.action_show_pass, v -> {
                        Intent intent = new Intent(EventDetailActivity.this, EventPassActivity.class);
                        intent.putExtra(EventPassActivity.EXTRA_EVENT_ID, eventId);
                        startActivity(intent);
                    });
                }
                spacer.show();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                ToastCenter.show(EventDetailActivity.this, message);
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
                String title = current != null && current.getTitle() != null
                        ? current.getTitle() : "the event";
                ToastCenter.showShort(EventDetailActivity.this,
                        "Registration cancelled for " + title + ".");
                Snackbar.make(binding.getRoot(), R.string.msg_cancelled, Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                ToastCenter.show(EventDetailActivity.this, message);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}

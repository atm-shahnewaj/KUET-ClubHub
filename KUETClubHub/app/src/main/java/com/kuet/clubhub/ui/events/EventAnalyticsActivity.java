package com.kuet.clubhub.ui.events;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.CheckInRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityEventAnalyticsBinding;
import com.kuet.clubhub.model.CheckIn;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.Participant;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.CsvExporter;
import com.kuet.clubhub.util.EventAnalytics;
import com.kuet.clubhub.util.Io;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Live analytics and CSV export for an event (Proposal features 12, 33, 34):
 * <ul>
 *   <li>Attendance vs. registration rate (and no-show count once the event has ended).</li>
 *   <li>Registrations over time (daily sign-up bars).</li>
 *   <li>Department breakdown (active participants grouped by department).</li>
 *   <li>One-tap CSV export shared through {@link FileProvider} and an implicit Intent.</li>
 * </ul>
 */
public class EventAnalyticsActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_TITLE = "event_title";

    private ActivityEventAnalyticsBinding binding;
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
        binding = ActivityEventAnalyticsBinding.inflate(getLayoutInflater());
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

        binding.barsOverview.setUnitLabel("participants");
        binding.barsTimeline.setUnitLabel("registrations");
        binding.barsDepartments.setUnitLabel("students");

        binding.btnExportCsv.setOnClickListener(v -> exportCsv());
        render();
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

    private List<Participant> mergedParticipants() {
        for (Participant p : participants) {
            CheckIn c = checkIns.get(p.getUid());
            p.setCheckedIn(c != null);
            p.setCheckedInAt(c == null ? null : c.getTimestamp());
        }
        return participants;
    }

    private void render() {
        if (binding == null) {
            return;
        }
        List<Participant> merged = mergedParticipants();
        long capacity = event == null ? 0 : event.getCapacity();
        boolean ended = event != null && event.hasEnded(System.currentTimeMillis());

        EventAnalytics.Summary summary = EventAnalytics.summarize(merged, capacity);
        binding.tvSubtitle.setText(getString(R.string.analytics_subtitle,
                summary.registered, summary.checkedIn, summary.waitlisted));
        binding.tvOverviewRates.setText(getString(R.string.analytics_rates_summary,
                summary.fillPercent, summary.attendancePercent));

        String missingLabel = getString(ended
                ? R.string.analytics_label_no_shows : R.string.analytics_label_not_yet);
        List<String> overviewLabels = Arrays.asList(
                getString(R.string.stat_registered),
                getString(R.string.status_checked_in),
                missingLabel,
                getString(R.string.stat_waiting));
        long[] overviewCounts = {
                summary.registered,
                summary.checkedIn,
                summary.missing,
                summary.waitlisted
        };
        long denom = Math.max(Math.max(capacity, summary.registered), 1L);
        binding.barsOverview.setDataWithDenominator(
                "overview", overviewLabels, overviewCounts, denom, -1);

        List<EventAnalytics.Bucket> timeline = EventAnalytics.registrationsByDay(merged);
        if (timeline.isEmpty()) {
            binding.barsTimeline.setVisibility(View.GONE);
            binding.tvEmptyTimeline.setVisibility(View.VISIBLE);
        } else {
            binding.barsTimeline.setVisibility(View.VISIBLE);
            binding.tvEmptyTimeline.setVisibility(View.GONE);
            binding.barsTimeline.setData("timeline_" + timeline.size(),
                    EventAnalytics.labels(timeline), EventAnalytics.counts(timeline), -1);
        }

        List<EventAnalytics.Bucket> departments = EventAnalytics.departmentBreakdown(merged);
        if (departments.isEmpty()) {
            binding.barsDepartments.setVisibility(View.GONE);
            binding.tvEmptyDepartments.setVisibility(View.VISIBLE);
        } else {
            binding.barsDepartments.setVisibility(View.VISIBLE);
            binding.tvEmptyDepartments.setVisibility(View.GONE);
            binding.barsDepartments.setData("depts_" + departments.size(),
                    EventAnalytics.labels(departments), EventAnalytics.counts(departments), -1);
        }
    }

    private void exportCsv() {
        List<Participant> merged = new ArrayList<>(mergedParticipants());
        boolean ended = event != null && event.hasEnded(System.currentTimeMillis());
        shareCsv(this, binding.getRoot(), eventId, eventTitle, merged, ended);
    }

    /**
     * Shared helper used by both {@link EventAnalyticsActivity} and {@link EventDashboardActivity}:
     * writes the CSV on {@link Io#DISK} and opens the system share sheet via {@link FileProvider}.
     */
    public static void shareCsv(final BaseActivity activity, final View rootView,
                                final String eventId, final String eventTitle,
                                final List<Participant> participants, final boolean eventEnded) {
        if (participants == null || participants.isEmpty()) {
            Snackbar.make(rootView, R.string.msg_csv_empty, Snackbar.LENGTH_SHORT).show();
            return;
        }
        final String csv = CsvExporter.formatParticipantsCsv(participants, eventEnded);
        final Context app = activity.getApplicationContext();
        Io.DISK.execute(() -> {
            try {
                final File file = CsvExporter.writeParticipantsCsv(app, eventId, csv);
                activity.runOnUiThread(() -> {
                    if (activity.isDestroyed()) {
                        return;
                    }
                    Uri uri = FileProvider.getUriForFile(activity,
                            activity.getPackageName() + ".fileprovider", file);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/csv");
                    send.putExtra(Intent.EXTRA_SUBJECT,
                            activity.getString(R.string.csv_share_subject,
                                    eventTitle == null || eventTitle.isEmpty() ? "Event" : eventTitle));
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(Intent.createChooser(
                            send, activity.getString(R.string.action_export_csv)));
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed()) {
                        Toast.makeText(activity, R.string.msg_csv_failed, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showError(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}

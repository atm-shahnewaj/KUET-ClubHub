package com.kuet.clubhub.ui.events;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.ClubRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityEventEditBinding;
import com.kuet.clubhub.model.Club;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.EventType;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.PickerUtil;
import com.kuet.clubhub.util.TimeFormats;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Creates an event (no extra), clones an existing one with new dates ({@link #EXTRA_CLONE_FROM_ID}),
 * or edits one ({@link #EXTRA_EVENT_ID}). Organizers of the club and admins only.
 */
public class EventEditActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_CLONE_FROM_ID = "clone_from_id";

    private static final String S_CLUB_ID = "s_club_id";
    private static final String S_CLUB_NAME = "s_club_name";
    private static final String S_CLUB_ORGS = "s_club_orgs";
    private static final String S_DATE = "s_date";
    private static final String S_START = "s_start";
    private static final String S_END = "s_end";
    private static final String S_DEADLINE = "s_deadline";
    private static final String S_REGISTERED = "s_registered";

    private static final long MINUTE = 60_000L;

    private ActivityEventEditBinding binding;
    private EventRepository events;
    private ClubRepository clubs;
    private UserProfile profile;
    private String eventId;
    private String cloneFromId;

    private String clubId;
    private String clubName;
    private ArrayList<String> clubOrganizers = new ArrayList<>();
    private long dateMillis = 0;      // local midnight of the event day, 0 = not chosen
    private int startMinutes = -1;    // minutes after midnight, -1 = not chosen
    private int endMinutes = -1;
    private long deadlineMillis = 0;  // local midnight of the deadline day, 0 = not chosen
    private long currentRegistered = 0;
    private final List<Club> availableClubs = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        profile = new SessionManager(this).getProfile();
        if (profile == null || profile.getRoleEnum() == Role.STUDENT) {
            finish();
            return;
        }
        events = EventRepository.getInstance(this);
        clubs = ClubRepository.getInstance(this);
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        cloneFromId = getIntent().getStringExtra(EXTRA_CLONE_FROM_ID);
        boolean editing = eventId != null;
        boolean cloning = !editing && cloneFromId != null;

        binding.toolbar.setTitle(editing ? R.string.title_edit_event
                : cloning ? R.string.title_clone_event : R.string.title_new_event);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (savedInstanceState != null) {
            clubId = savedInstanceState.getString(S_CLUB_ID);
            clubName = savedInstanceState.getString(S_CLUB_NAME);
            ArrayList<String> orgs = savedInstanceState.getStringArrayList(S_CLUB_ORGS);
            clubOrganizers = orgs == null ? new ArrayList<>() : orgs;
            dateMillis = savedInstanceState.getLong(S_DATE);
            startMinutes = savedInstanceState.getInt(S_START, -1);
            endMinutes = savedInstanceState.getInt(S_END, -1);
            deadlineMillis = savedInstanceState.getLong(S_DEADLINE);
            currentRegistered = savedInstanceState.getLong(S_REGISTERED);
        }

        setupPickers(editing);
        updateCompetitionVisibility();
        binding.btnSave.setOnClickListener(v -> save());

        if (editing) {
            binding.tilClub.setEnabled(false);
            binding.btnSave.setEnabled(false);
            if (savedInstanceState == null) {
                loadForEdit(eventId, false);
            } else {
                binding.btnSave.setEnabled(true);
            }
        } else if (cloning) {
            loadClubs(false);
            if (savedInstanceState == null) {
                binding.btnSave.setEnabled(false);
                loadForEdit(cloneFromId, true);
            }
        } else {
            loadClubs(savedInstanceState == null);
        }
    }

    @Override
    protected void onSaveInstanceState(android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(S_CLUB_ID, clubId);
        outState.putString(S_CLUB_NAME, clubName);
        outState.putStringArrayList(S_CLUB_ORGS, clubOrganizers);
        outState.putLong(S_DATE, dateMillis);
        outState.putInt(S_START, startMinutes);
        outState.putInt(S_END, endMinutes);
        outState.putLong(S_DEADLINE, deadlineMillis);
        outState.putLong(S_REGISTERED, currentRegistered);
    }

    // ------------------------------------------------------------------ loading

    private void loadClubs(boolean autoSelect) {
        clubs.loadClubsForEvents(profile.getRoleEnum() == Role.ADMIN, profile.getUid(),
                new Callback<List<Club>>() {
                    @Override
                    public void onSuccess(List<Club> result) {
                        availableClubs.clear();
                        availableClubs.addAll(result);
                        if (result.isEmpty()) {
                            Snackbar.make(binding.getRoot(), R.string.msg_no_clubs_for_events,
                                    Snackbar.LENGTH_INDEFINITE).show();
                        } else if (autoSelect && result.size() == 1 && clubId == null) {
                            selectClub(result.get(0));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void loadForEdit(String sourceId, boolean asClone) {
        events.loadEvent(sourceId, new Callback<Event>() {
            @Override
            public void onSuccess(Event e) {
                clubId = e.getClubId();
                clubName = e.getClubName();
                clubOrganizers = new ArrayList<>(
                        e.getOrganizers() == null ? new ArrayList<String>() : e.getOrganizers());
                currentRegistered = asClone ? 0 : e.getRegisteredCount();

                binding.etClub.setText(clubName);
                binding.etTitle.setText(e.getTitle());
                binding.etType.setText(e.typeEnum().getLabel());
                binding.etDescription.setText(e.getDescription());
                binding.etVenue.setText(e.getVenue());
                binding.etCapacity.setText(String.valueOf(e.getCapacity()));
                binding.etTags.setText(e.getTags() == null ? ""
                        : android.text.TextUtils.join(", ", e.getTags()));
                binding.etPrizes.setText(e.getPrizes());
                binding.etRules.setText(e.getRules());
                binding.etTeamMin.setText(e.getTeamSizeMin() > 0 ? String.valueOf(e.getTeamSizeMin()) : "");
                binding.etTeamMax.setText(e.getTeamSizeMax() > 0 ? String.valueOf(e.getTeamSizeMax()) : "");

                if (e.getStartTime() != null) {
                    startMinutes = minutesOfDay(e.getStartTime());
                    if (!asClone) {
                        dateMillis = midnightOf(e.getStartTime().getTime());
                    }
                }
                if (e.getEndTime() != null) {
                    endMinutes = minutesOfDay(e.getEndTime());
                }
                if (!asClone) {
                    deadlineMillis = e.getRegistrationDeadline() == null
                            ? 0 : midnightOf(e.getRegistrationDeadline().getTime());
                } else {
                    dateMillis = 0;
                    deadlineMillis = 0;
                    Snackbar.make(binding.getRoot(), R.string.msg_clone_pick_dates,
                            Snackbar.LENGTH_LONG).show();
                }

                refreshDateTimeFields();
                updateCompetitionVisibility();
                binding.btnSave.setEnabled(true);
            }

            @Override
            public void onError(String message) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------------ pickers

    private void setupPickers(boolean editing) {
        View.OnClickListener pickClub = v -> {
            if (availableClubs.isEmpty()) {
                Snackbar.make(binding.getRoot(), R.string.msg_no_clubs_for_events,
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            String[] names = new String[availableClubs.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = availableClubs.get(i).getName();
            }
            PickerUtil.showChoice(this, getString(R.string.title_pick_club), names, clubName,
                    picked -> {
                        for (Club c : availableClubs) {
                            if (picked.equals(c.getName())) {
                                selectClub(c);
                                break;
                            }
                        }
                    });
        };
        if (!editing) {
            binding.etClub.setOnClickListener(pickClub);
            binding.tilClub.setEndIconOnClickListener(pickClub);
        }

        View.OnClickListener pickType = v -> PickerUtil.showChoice(
                this, getString(R.string.title_pick_event_type), EventType.labels(),
                textOf(binding.etType), picked -> {
                    binding.etType.setText(picked);
                    updateCompetitionVisibility();
                });
        binding.etType.setOnClickListener(pickType);
        binding.tilType.setEndIconOnClickListener(pickType);

        View.OnClickListener pickDate = v -> showDatePicker(false, editing);
        binding.etDate.setOnClickListener(pickDate);
        binding.tilDate.setEndIconOnClickListener(pickDate);

        View.OnClickListener pickStart = v -> showTimePicker(true);
        binding.etStart.setOnClickListener(pickStart);
        binding.tilStart.setEndIconOnClickListener(pickStart);

        View.OnClickListener pickEnd = v -> showTimePicker(false);
        binding.etEnd.setOnClickListener(pickEnd);
        binding.tilEnd.setEndIconOnClickListener(pickEnd);

        View.OnClickListener pickDeadline = v -> showDatePicker(true, editing);
        binding.etDeadline.setOnClickListener(pickDeadline);
        binding.tilDeadline.setEndIconOnClickListener(pickDeadline);
    }

    private void selectClub(Club club) {
        clubId = club.getId();
        clubName = club.getName();
        clubOrganizers = new ArrayList<>(
                club.getOrganizers() == null ? new ArrayList<String>() : club.getOrganizers());
        binding.etClub.setText(clubName);
        binding.tilClub.setError(null);
    }

    private void showDatePicker(boolean forDeadline, boolean editing) {
        long current = forDeadline ? deadlineMillis : dateMillis;
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(forDeadline ? R.string.title_pick_deadline : R.string.title_pick_date)
                .setSelection(current == 0
                        ? MaterialDatePicker.todayInUtcMilliseconds() : localMidnightToUtc(current));
        if (!editing) {
            builder.setCalendarConstraints(new CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now()).build());
        }
        MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            long local = utcToLocalMidnight(selection);
            if (forDeadline) {
                deadlineMillis = local;
                binding.tilDeadline.setError(null);
            } else {
                dateMillis = local;
                binding.tilDate.setError(null);
            }
            refreshDateTimeFields();
        });
        picker.show(getSupportFragmentManager(), forDeadline ? "deadline" : "date");
    }

    private void showTimePicker(boolean isStart) {
        int minutes = isStart ? startMinutes : endMinutes;
        if (minutes < 0) {
            minutes = isStart ? 10 * 60 : 12 * 60;
        }
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(minutes / 60)
                .setMinute(minutes % 60)
                .setTitleText(isStart ? R.string.title_pick_start : R.string.title_pick_end)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            int chosen = picker.getHour() * 60 + picker.getMinute();
            if (isStart) {
                startMinutes = chosen;
                binding.tilStart.setError(null);
            } else {
                endMinutes = chosen;
                binding.tilEnd.setError(null);
            }
            refreshDateTimeFields();
        });
        picker.show(getSupportFragmentManager(), isStart ? "start" : "end");
    }

    private void refreshDateTimeFields() {
        binding.etDate.setText(dateMillis == 0 ? "" : TimeFormats.fullDate(new Date(dateMillis)));
        binding.etStart.setText(startMinutes < 0 ? "" : formatMinutes(startMinutes));
        binding.etEnd.setText(endMinutes < 0 ? "" : formatMinutes(endMinutes));
        binding.etDeadline.setText(deadlineMillis == 0 ? "" : TimeFormats.fullDate(new Date(deadlineMillis)));
    }

    private void updateCompetitionVisibility() {
        boolean competition = EventType.fromLabel(textOf(binding.etType)) == EventType.COMPETITION;
        binding.competitionBlock.setVisibility(competition ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ saving

    private void save() {
        String title = textOf(binding.etTitle);
        EventType type = EventType.fromLabel(textOf(binding.etType));
        String description = textOf(binding.etDescription);
        String venue = textOf(binding.etVenue);
        String capacityText = textOf(binding.etCapacity);
        boolean competition = type == EventType.COMPETITION;

        binding.tilClub.setError(null);
        binding.tilTitle.setError(null);
        binding.tilType.setError(null);
        binding.tilDescription.setError(null);
        binding.tilDate.setError(null);
        binding.tilStart.setError(null);
        binding.tilEnd.setError(null);
        binding.tilVenue.setError(null);
        binding.tilCapacity.setError(null);
        binding.tilDeadline.setError(null);
        binding.tilTeamMin.setError(null);
        binding.tilTeamMax.setError(null);

        boolean valid = true;
        if (clubId == null) {
            binding.tilClub.setError(getString(R.string.error_club_required));
            valid = false;
        }
        if (title.length() < 3) {
            binding.tilTitle.setError(getString(R.string.error_event_title_short));
            valid = false;
        }
        if (type == null) {
            binding.tilType.setError(getString(R.string.error_event_type_required));
            valid = false;
        }
        if (description.isEmpty()) {
            binding.tilDescription.setError(getString(R.string.error_description_required));
            valid = false;
        }
        if (dateMillis == 0) {
            binding.tilDate.setError(getString(R.string.error_date_required));
            valid = false;
        }
        if (startMinutes < 0) {
            binding.tilStart.setError(getString(R.string.error_time_required));
            valid = false;
        }
        if (endMinutes < 0) {
            binding.tilEnd.setError(getString(R.string.error_time_required));
            valid = false;
        } else if (startMinutes >= 0 && endMinutes <= startMinutes) {
            binding.tilEnd.setError(getString(R.string.error_end_before_start));
            valid = false;
        }
        if (venue.isEmpty()) {
            binding.tilVenue.setError(getString(R.string.error_venue_required));
            valid = false;
        }

        long capacity = parseLong(capacityText);
        if (capacity < 1 || capacity > 5000) {
            binding.tilCapacity.setError(getString(R.string.error_capacity_invalid));
            valid = false;
        } else if (eventId != null && capacity < currentRegistered) {
            binding.tilCapacity.setError(getString(R.string.error_capacity_below_registered,
                    (int) currentRegistered));
            valid = false;
        }

        long startMillis = dateMillis == 0 || startMinutes < 0 ? 0 : dateMillis + startMinutes * MINUTE;
        if (valid && eventId == null && startMillis <= System.currentTimeMillis()) {
            binding.tilDate.setError(getString(R.string.error_event_in_past));
            valid = false;
        }

        int teamMin = 0;
        int teamMax = 0;
        long deadlineEnd = 0;
        if (competition) {
            if (deadlineMillis == 0) {
                binding.tilDeadline.setError(getString(R.string.error_deadline_required));
                valid = false;
            } else {
                deadlineEnd = deadlineMillis + (24 * 60 - 1) * MINUTE;
                if (startMillis != 0 && deadlineEnd > startMillis) {
                    binding.tilDeadline.setError(getString(R.string.error_deadline_after_start));
                    valid = false;
                }
            }
            teamMin = (int) parseLong(textOf(binding.etTeamMin));
            teamMax = (int) parseLong(textOf(binding.etTeamMax));
            if (teamMin < 1 || teamMin > 20) {
                binding.tilTeamMin.setError(getString(R.string.error_team_size_invalid));
                valid = false;
            }
            if (teamMax < Math.max(1, teamMin) || teamMax > 20) {
                binding.tilTeamMax.setError(getString(R.string.error_team_size_invalid));
                valid = false;
            }
        }
        if (!valid) {
            return;
        }

        Event e = new Event();
        e.setClubId(clubId);
        e.setClubName(clubName);
        e.setOrganizers(new ArrayList<>(clubOrganizers));
        e.setTitle(title);
        e.setType(type.getValue());
        e.setDescription(description);
        e.setVenue(venue);
        e.setCapacity(capacity);
        e.setStartTime(new Date(startMillis));
        e.setEndTime(new Date(dateMillis + endMinutes * MINUTE));
        e.setTags(parseTags(textOf(binding.etTags)));
        e.setTeamSizeMin(competition ? teamMin : 0);
        e.setTeamSizeMax(competition ? teamMax : 0);
        e.setPrizes(competition ? textOf(binding.etPrizes) : "");
        e.setRules(competition ? textOf(binding.etRules) : "");
        e.setRegistrationDeadline(competition ? new Date(deadlineEnd) : null);

        setLoading(true);
        if (eventId == null) {
            events.createEvent(e, profile.getUid(), new Callback<String>() {
                @Override
                public void onSuccess(String id) {
                    Toast.makeText(EventEditActivity.this, R.string.msg_event_created,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        } else {
            events.updateEvent(eventId, e, new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(EventEditActivity.this, R.string.msg_event_updated,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        }
    }

    private void showError(String message) {
        setLoading(false);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> parseTags(String text) {
        Set<String> tags = new LinkedHashSet<>();
        for (String part : text.split("[,\\s]+")) {
            String tag = part.trim().toLowerCase(Locale.ROOT);
            if (tag.startsWith("#")) {
                tag = tag.substring(1);
            }
            if (!tag.isEmpty() && tags.size() < 10) {
                tags.add(tag);
            }
        }
        return new ArrayList<>(tags);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatMinutes(int minutes) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minutes / 60);
        c.set(Calendar.MINUTE, minutes % 60);
        return TimeFormats.time(c.getTime());
    }

    private static int minutesOfDay(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private static long midnightOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** MaterialDatePicker speaks UTC midnight; the app stores local midnight. */
    private static long utcToLocalMidnight(long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
    }

    private static long localMidnightToUtc(long localMillis) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localMillis);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    private static String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}

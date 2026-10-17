package com.kuet.clubhub.ui.events;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.data.ClubRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.local.EventEntity;
import com.kuet.clubhub.databinding.FragmentEventsBinding;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.EventType;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.TimeFormats;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All events, as a searchable list or as a month calendar. Data comes from Room (offline-safe). */
public class EventsFragment extends Fragment {

    private static final int CHIP_ID_BASE = 2000;

    private FragmentEventsBinding binding;
    private EventAdapter adapter;
    private EventRepository repository;
    private UserProfile profile;

    private final List<Event> allEvents = new ArrayList<>();
    private String query = "";
    private EventType typeFilter = null; // null = all
    private boolean calendarMode = false;
    private boolean loaded = false;
    private boolean offlineEmpty = false;

    private final Calendar visibleMonth = Calendar.getInstance();
    private int selectedDay;

    private ListenerRegistration syncRegistration;
    private ListenerRegistration myRegsRegistration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEventsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = EventRepository.getInstance(requireContext());
        profile = new SessionManager(requireContext()).getProfile();

        visibleMonth.set(Calendar.DAY_OF_MONTH, 1);
        selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

        adapter = new EventAdapter(true, this::openEvent);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);

        boolean canCreate = profile != null && profile.getRoleEnum() != Role.STUDENT;
        binding.fabNewEvent.setVisibility(canCreate ? View.VISIBLE : View.GONE);
        binding.fabNewEvent.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EventEditActivity.class)));

        setupSearchAndChips();
        setupCalendar();

        binding.toggleView.check(R.id.btn_view_list);
        binding.toggleView.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                calendarMode = checkedId == R.id.btn_view_calendar;
                render();
            }
        });

        repository.observeCachedEvents().observe(getViewLifecycleOwner(), this::onCacheChanged);
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (profile == null) {
            return;
        }
        syncRegistration = repository.startSync(new ClubRepository.SyncListener() {
            @Override
            public void onSynced() {
                loaded = true;
                offlineEmpty = false;
                render();
            }

            @Override
            public void onOfflineEmpty() {
                loaded = true;
                offlineEmpty = true;
                render();
            }

            @Override
            public void onError(String message) {
                loaded = true;
                render();
                showMessage(message);
            }
        });
        myRegsRegistration = repository.listenMyRegistrations(profile.getUid(),
                new EventRepository.MyRegistrationsListener() {
                    @Override
                    public void onChanged(List<MyRegistration> registrations) {
                        Map<String, String> status = new HashMap<>();
                        for (MyRegistration r : registrations) {
                            if (r.isActive()) {
                                status.put(r.getEventId(), r.getStatus());
                            }
                        }
                        if (adapter != null) {
                            adapter.setMyStatus(status);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Badges are a nicety; the list still works without them.
                    }
                });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (syncRegistration != null) {
            syncRegistration.remove();
            syncRegistration = null;
        }
        if (myRegsRegistration != null) {
            myRegsRegistration.remove();
            myRegsRegistration = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ------------------------------------------------------------------ setup

    private void setupSearchAndChips() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        addChip(getString(R.string.chip_all), null, 0, true);
        EventType[] types = EventType.values();
        for (int i = 0; i < types.length; i++) {
            addChip(types[i].getLabel(), types[i], i + 1, false);
        }
        binding.chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            Chip chip = group.findViewById(checkedIds.get(0));
            typeFilter = chip == null ? null : (EventType) chip.getTag();
            render();
        });
    }

    private void addChip(String label, @Nullable EventType tag, int index, boolean checked) {
        Chip chip = (Chip) getLayoutInflater()
                .inflate(R.layout.item_filter_chip, binding.chipGroup, false);
        chip.setId(CHIP_ID_BASE + index);
        chip.setText(label);
        chip.setTag(tag);
        chip.setChecked(checked);
        binding.chipGroup.addView(chip);
    }

    private void setupCalendar() {
        binding.btnPrevMonth.setOnClickListener(v -> shiftMonth(-1));
        binding.btnNextMonth.setOnClickListener(v -> shiftMonth(1));
        binding.calendar.setOnDayClickListener((year, month, day) -> {
            selectedDay = day;
            render();
        });
    }

    private void shiftMonth(int delta) {
        visibleMonth.add(Calendar.MONTH, delta);
        Calendar now = Calendar.getInstance();
        boolean sameMonth = now.get(Calendar.YEAR) == visibleMonth.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == visibleMonth.get(Calendar.MONTH);
        selectedDay = sameMonth ? now.get(Calendar.DAY_OF_MONTH) : 1;
        render();
    }

    // ------------------------------------------------------------------ data

    private void onCacheChanged(List<EventEntity> entities) {
        allEvents.clear();
        for (EventEntity e : entities) {
            allEvents.add(e.toEvent());
        }
        render();
    }

    private void openEvent(Event event) {
        Intent intent = new Intent(requireContext(), EventDetailActivity.class);
        intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.getId());
        startActivity(intent);
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        if (binding == null) {
            return;
        }
        binding.listControls.setVisibility(calendarMode ? View.GONE : View.VISIBLE);
        binding.calendarBlock.setVisibility(calendarMode ? View.VISIBLE : View.GONE);
        binding.tvDayTitle.setVisibility(calendarMode ? View.VISIBLE : View.GONE);

        List<Event> shown;
        if (calendarMode) {
            renderCalendar();
            shown = eventsOnSelectedDay();
        } else {
            shown = filteredUpcomingFirst();
        }
        adapter.submitList(shown);

        boolean empty = shown.isEmpty();
        boolean showSpinner = empty && allEvents.isEmpty() && !loaded;
        binding.progress.setVisibility(showSpinner ? View.VISIBLE : View.GONE);
        binding.emptyContainer.setVisibility(empty && !showSpinner ? View.VISIBLE : View.GONE);
        binding.emptyState.setText(emptyMessage());
    }

    private void renderCalendar() {
        int year = visibleMonth.get(Calendar.YEAR);
        int month = visibleMonth.get(Calendar.MONTH);
        binding.tvMonthTitle.setText(TimeFormats.monthYear(visibleMonth));
        binding.calendar.setMonth(year, month);
        binding.calendar.setSelectedDay(selectedDay);

        Map<Integer, List<Integer>> dots = new HashMap<>();
        Calendar c = Calendar.getInstance();
        for (Event e : allEvents) {
            if (e.getStartTime() == null) {
                continue;
            }
            c.setTime(e.getStartTime());
            if (c.get(Calendar.YEAR) != year || c.get(Calendar.MONTH) != month) {
                continue;
            }
            int day = c.get(Calendar.DAY_OF_MONTH);
            int color = ContextCompat.getColor(requireContext(), e.typeEnum().getColorRes());
            List<Integer> colors = dots.get(day);
            if (colors == null) {
                colors = new ArrayList<>();
                dots.put(day, colors);
            }
            if (!colors.contains(color)) {
                colors.add(color);
            }
        }
        binding.calendar.setEventDots(dots);

        Calendar selected = (Calendar) visibleMonth.clone();
        selected.set(Calendar.DAY_OF_MONTH, selectedDay);
        binding.tvDayTitle.setText(getString(R.string.events_on_day,
                TimeFormats.fullDate(selected.getTime())));
    }

    private List<Event> eventsOnSelectedDay() {
        int year = visibleMonth.get(Calendar.YEAR);
        int month = visibleMonth.get(Calendar.MONTH);
        List<Event> out = new ArrayList<>();
        for (Event e : allEvents) {
            if (TimeFormats.sameDay(e.getStartTime(), year, month, selectedDay)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Search + type filter; upcoming events first (soonest first), finished ones last. */
    private List<Event> filteredUpcomingFirst() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Event> upcoming = new ArrayList<>();
        List<Event> past = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Event e : allEvents) {
            if (typeFilter != null && e.typeEnum() != typeFilter) {
                continue;
            }
            if (!q.isEmpty() && !matches(e, q)) {
                continue;
            }
            if (e.hasEnded(now)) {
                past.add(e);
            } else {
                upcoming.add(e);
            }
        }
        Collections.sort(upcoming, (a, b) -> compareDates(a.getStartTime(), b.getStartTime()));
        Collections.sort(past, (a, b) -> compareDates(b.getStartTime(), a.getStartTime()));
        upcoming.addAll(past);
        return upcoming;
    }

    private static int compareDates(Date a, Date b) {
        long x = a == null ? 0 : a.getTime();
        long y = b == null ? 0 : b.getTime();
        return Long.compare(x, y);
    }

    private boolean matches(Event e, String q) {
        if (lower(e.getTitle()).contains(q) || lower(e.getClubName()).contains(q)
                || lower(e.getVenue()).contains(q) || lower(e.getDescription()).contains(q)) {
            return true;
        }
        if (e.getTags() != null) {
            for (String tag : e.getTags()) {
                if (lower(tag).contains(q)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private int emptyMessage() {
        if (calendarMode) {
            return R.string.empty_events_day;
        }
        boolean filtering = !query.trim().isEmpty() || typeFilter != null;
        if (filtering && !allEvents.isEmpty()) {
            return R.string.empty_events_filtered;
        }
        return offlineEmpty ? R.string.empty_events_offline : R.string.empty_events_all;
    }

    private void showMessage(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}

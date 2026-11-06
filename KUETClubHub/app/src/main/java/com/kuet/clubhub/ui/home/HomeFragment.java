package com.kuet.clubhub.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.data.ClubRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.data.local.ClubEntity;
import com.kuet.clubhub.data.local.EventEntity;
import com.kuet.clubhub.databinding.FragmentHomeBinding;
import com.kuet.clubhub.databinding.ItemEventCardHomeBinding;
import com.kuet.clubhub.model.Club;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.MainActivity;
import com.kuet.clubhub.ui.alerts.AlertsActivity;
import com.kuet.clubhub.ui.certificate.VerifyCertificateActivity;
import com.kuet.clubhub.ui.clubs.ClubAdapter;
import com.kuet.clubhub.ui.clubs.ClubDetailActivity;
import com.kuet.clubhub.ui.events.EventDetailActivity;
import com.kuet.clubhub.ui.profile.EditProfileActivity;
import com.kuet.clubhub.util.ImageHelper;
import com.kuet.clubhub.util.ThemeManager;
import com.kuet.clubhub.util.TimeFormats;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Redesigned Home screen matching UI.jpg:
 * - User avatar + time-of-day greeting + theme toggle + notification bell
 * - Search bar + Category pills (All, Technical, Cyber, Debate, Cultural, Sports)
 * - Active Events horizontal banner cards
 * - Quick Campus Actions (My Pass, Scan QR, Verify Cert)
 * - Featured Clubs with Club Logos
 */
public class HomeFragment extends Fragment {

    private static final String[] HOME_CATEGORIES = {
            "All", "Technical", "Cyber", "Debate", "Cultural", "Sports"
    };

    private FragmentHomeBinding binding;
    private final List<Event> allEvents = new ArrayList<>();
    private final List<Club> allClubs = new ArrayList<>();
    private String query = "";
    private String selectedCategory = "All";

    private HomeEventCardAdapter eventAdapter;
    private ClubAdapter clubAdapter;
    private ListenerRegistration clubSync;
    private ListenerRegistration eventSync;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.cardAvatar.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        binding.btnTheme.setOnClickListener(v -> ThemeManager.toggle(requireContext()));
        binding.btnAlerts.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AlertsActivity.class)));

        AppDatabase.getInstance(requireContext()).alertDao().observeUnreadCount()
                .observe(getViewLifecycleOwner(), count -> {
                    if (binding != null) {
                        binding.btnAlerts.setImageResource(count != null && count > 0
                                ? R.drawable.ic_bell_unread : R.drawable.ic_bell_dark);
                    }
                });

        setupCategories();

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                render();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        eventAdapter = new HomeEventCardAdapter();
        binding.recyclerActiveEvents.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerActiveEvents.setAdapter(eventAdapter);

        clubAdapter = new ClubAdapter(club -> {
            Intent intent = new Intent(requireContext(), ClubDetailActivity.class);
            intent.putExtra(ClubDetailActivity.EXTRA_CLUB_ID, club.getId());
            startActivity(intent);
        });
        binding.recyclerFeaturedClubs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerFeaturedClubs.setAdapter(clubAdapter);

        binding.btnSeeAllEvents.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(R.id.nav_events);
            }
        });
        binding.btnSeeAllClubs.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(R.id.nav_clubs);
            }
        });

        binding.btnQuickPass.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openQuickShortcut(MainActivity.ACTION_SHORTCUT_MY_PASS);
            }
        });
        binding.btnQuickScan.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openQuickShortcut(MainActivity.ACTION_SHORTCUT_SCAN_QR);
            }
        });
        binding.btnQuickVerifyCert.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), VerifyCertificateActivity.class)));

        EventRepository.getInstance(requireContext()).observeCachedEvents()
                .observe(getViewLifecycleOwner(), entities -> {
                    allEvents.clear();
                    for (EventEntity e : entities) allEvents.add(e.toEvent());
                    render();
                });

        ClubRepository.getInstance(requireContext()).observeCachedClubs()
                .observe(getViewLifecycleOwner(), entities -> {
                    allClubs.clear();
                    for (ClubEntity c : entities) allClubs.add(c.toClub());
                    render();
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        bindHeaderProfile();
        ClubRepository.SyncListener noop = new ClubRepository.SyncListener() {
            @Override public void onSynced() {}
            @Override public void onOfflineEmpty() {}
            @Override public void onError(String message) {}
        };
        clubSync = ClubRepository.getInstance(requireContext()).startSync(noop);
        eventSync = EventRepository.getInstance(requireContext()).startSync(noop);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (clubSync != null) { clubSync.remove(); clubSync = null; }
        if (eventSync != null) { eventSync.remove(); eventSync = null; }
    }

    private void bindHeaderProfile() {
        if (binding == null) return;
        UserProfile p = new SessionManager(requireContext()).getProfile();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Good morning," : hour < 17 ? "Good afternoon," : "Good evening,";
        binding.tvGreetingPrefix.setText(greeting);

        String fullName = p == null || p.getName() == null ? "Student" : p.getName().trim();
        String first = fullName.isEmpty() ? "Student" : fullName.split("\\s+")[0];
        binding.tvGreetingName.setText(first + "!");

        ImageHelper.bindUserAvatar(binding.ivUserAvatar, binding.tvUserInitial,
                p == null ? null : p.getPhotoBase64(), fullName);
    }

    private void setupCategories() {
        binding.chipGroupCategory.removeAllViews();
        for (int i = 0; i < HOME_CATEGORIES.length; i++) {
            String cat = HOME_CATEGORIES[i];
            Chip chip = (Chip) getLayoutInflater()
                    .inflate(R.layout.item_filter_chip, binding.chipGroupCategory, false);
            chip.setId(View.generateViewId());
            chip.setText(cat);
            chip.setTag(cat);
            chip.setChecked(i == 0);
            binding.chipGroupCategory.addView(chip);
        }
        binding.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip c = group.findViewById(checkedIds.get(0));
            selectedCategory = c == null ? "All" : (String) c.getTag();
            render();
        });
    }

    private void render() {
        if (binding == null) return;
        String q = query.trim().toLowerCase(Locale.ROOT);
        String cat = "All".equals(selectedCategory) ? "" : selectedCategory.toLowerCase(Locale.ROOT);

        List<Event> filteredEvents = new ArrayList<>();
        for (Event e : allEvents) {
            if (!cat.isEmpty() && !matchesEvent(e, cat)) continue;
            if (!q.isEmpty() && !matchesEvent(e, q)) continue;
            filteredEvents.add(e);
        }
        Collections.sort(filteredEvents, (a, b) -> {
            long x = a.getStartTime() == null ? 0 : a.getStartTime().getTime();
            long y = b.getStartTime() == null ? 0 : b.getStartTime().getTime();
            return Long.compare(x, y);
        });
        eventAdapter.submit(filteredEvents);
        binding.tvEmptyEvents.setVisibility(filteredEvents.isEmpty() ? View.VISIBLE : View.GONE);

        List<Club> filteredClubs = new ArrayList<>();
        for (Club c : allClubs) {
            if (!cat.isEmpty() && !lower(c.getCategory()).contains(cat)
                    && !lower(c.getDescription()).contains(cat)) continue;
            if (!q.isEmpty() && !lower(c.getName()).contains(q)
                    && !lower(c.getDescription()).contains(q)) continue;
            filteredClubs.add(c);
        }
        clubAdapter.submitList(filteredClubs.size() > 5 ? filteredClubs.subList(0, 5) : filteredClubs);
    }

    private boolean matchesEvent(Event e, String term) {
        if (lower(e.getTitle()).contains(term) || lower(e.getClubName()).contains(term)
                || lower(e.getVenue()).contains(term) || lower(e.getDescription()).contains(term)
                || lower(e.getType()).contains(term)) {
            return true;
        }
        if (e.getTags() != null) {
            for (String t : e.getTags()) {
                if (lower(t).contains(term)) return true;
            }
        }
        return false;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private class HomeEventCardAdapter extends RecyclerView.Adapter<HomeEventCardAdapter.Holder> {
        private List<Event> items = new ArrayList<>();

        void submit(List<Event> list) {
            this.items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemEventCardHomeBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Event e = items.get(position);
            ImageHelper.bindEventBanner(holder.binding.ivBanner, e.getBannerBase64(), e.typeEnum());
            holder.binding.tvTitle.setText(e.getTitle());
            holder.binding.tvOrganizer.setText("Organizer " + (e.getClubName() == null ? "KUET" : e.getClubName()));
            String venue = e.getVenue() == null ? "KUET" : e.getVenue();
            String date = TimeFormats.monthShort(e.getStartTime()) + " " + TimeFormats.dayNumber(e.getStartTime());
            holder.binding.tvVenueDate.setText(venue + ", " + date);
            holder.itemView.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), EventDetailActivity.class);
                i.putExtra(EventDetailActivity.EXTRA_EVENT_ID, e.getId());
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemEventCardHomeBinding binding;
            Holder(ItemEventCardHomeBinding b) {
                super(b.getRoot());
                this.binding = b;
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

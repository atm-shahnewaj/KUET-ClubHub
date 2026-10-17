package com.kuet.clubhub.ui.clubs;

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

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.data.ClubRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.local.ClubEntity;
import com.kuet.clubhub.databinding.FragmentClubListBinding;
import com.kuet.clubhub.model.Club;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.Categories;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One list screen, three modes:
 * ALL     - the approved-club directory (served from Room, synced from Firestore)
 * MINE    - clubs the signed-in organizer created (live from Firestore)
 * PENDING - clubs waiting for admin approval (live from Firestore)
 */
public class ClubListFragment extends Fragment {

    public static final int MODE_ALL = 0;
    public static final int MODE_MINE = 1;
    public static final int MODE_PENDING = 2;

    private static final String ARG_MODE = "mode";
    private static final int CHIP_ID_BASE = 1000;

    public static ClubListFragment newInstance(int mode) {
        ClubListFragment f = new ClubListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, mode);
        f.setArguments(args);
        return f;
    }

    private FragmentClubListBinding binding;
    private ClubAdapter adapter;
    private ClubRepository repository;
    private UserProfile profile;
    private int mode;

    private final List<Club> allClubs = new ArrayList<>();
    private String query = "";
    private String category = null; // null = "All"
    private boolean loaded = false;
    private boolean offlineEmpty = false;
    private ListenerRegistration registration;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = requireArguments().getInt(ARG_MODE, MODE_ALL);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentClubListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = ClubRepository.getInstance(requireContext());
        profile = new SessionManager(requireContext()).getProfile();

        adapter = new ClubAdapter(this::openClub);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);

        boolean canCreate = profile != null && profile.getRoleEnum() != Role.STUDENT
                && mode != MODE_PENDING;
        binding.fabNewClub.setVisibility(canCreate ? View.VISIBLE : View.GONE);
        binding.fabNewClub.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ClubEditActivity.class)));

        if (mode == MODE_ALL) {
            String fullName = profile == null || profile.getName() == null
                    ? "" : profile.getName().trim();
            String firstName = fullName.isEmpty() ? "" : fullName.split("\\s+")[0];
            binding.tvGreeting.setText(firstName.isEmpty()
                    ? getString(R.string.title_clubs)
                    : getString(R.string.greeting_hello, firstName));
            setupSearchAndChips();
            // Room is the single source of truth for the directory.
            repository.observeCachedClubs().observe(getViewLifecycleOwner(), this::onCacheChanged);
        } else {
            binding.greetingBlock.setVisibility(View.GONE);
            binding.tilSearch.setVisibility(View.GONE);
            binding.chipScroll.setVisibility(View.GONE);
        }
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (profile == null) {
            return;
        }
        if (mode == MODE_ALL) {
            registration = repository.startSync(new ClubRepository.SyncListener() {
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
        } else {
            ClubRepository.ClubsListener listener = new ClubRepository.ClubsListener() {
                @Override
                public void onClubs(List<Club> clubs) {
                    allClubs.clear();
                    allClubs.addAll(clubs);
                    loaded = true;
                    render();
                }

                @Override
                public void onError(String message) {
                    loaded = true;
                    render();
                    showMessage(message);
                }
            };
            registration = mode == MODE_MINE
                    ? repository.listenMyClubs(profile.getUid(), listener)
                    : repository.listenPendingClubs(listener);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
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
        for (int i = 0; i < Categories.ALL.length; i++) {
            addChip(Categories.ALL[i], Categories.ALL[i], i + 1, false);
        }
        binding.chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            Chip chip = group.findViewById(checkedIds.get(0));
            category = chip == null ? null : (String) chip.getTag();
            render();
        });
    }

    private void addChip(String label, @Nullable String tag, int index, boolean checked) {
        Chip chip = (Chip) getLayoutInflater()
                .inflate(R.layout.item_filter_chip, binding.chipGroup, false);
        chip.setId(CHIP_ID_BASE + index);
        chip.setText(label);
        chip.setTag(tag);
        chip.setChecked(checked);
        binding.chipGroup.addView(chip);
    }

    // ------------------------------------------------------------------ data

    private void onCacheChanged(List<ClubEntity> entities) {
        allClubs.clear();
        for (ClubEntity e : entities) {
            allClubs.add(e.toClub());
        }
        render();
    }

    private void openClub(Club club) {
        Intent intent = new Intent(requireContext(), ClubDetailActivity.class);
        intent.putExtra(ClubDetailActivity.EXTRA_CLUB_ID, club.getId());
        startActivity(intent);
    }

    private void render() {
        if (binding == null) {
            return;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Club> filtered = new ArrayList<>();
        for (Club c : allClubs) {
            if (category != null && !category.equals(c.getCategory())) {
                continue;
            }
            if (!q.isEmpty() && !matches(c, q)) {
                continue;
            }
            filtered.add(c);
        }
        adapter.submitList(filtered);

        boolean empty = filtered.isEmpty();
        boolean showSpinner = empty && allClubs.isEmpty() && !loaded;
        binding.progress.setVisibility(showSpinner ? View.VISIBLE : View.GONE);
        binding.emptyContainer.setVisibility(empty && !showSpinner ? View.VISIBLE : View.GONE);
        binding.emptyState.setText(emptyMessage());
    }

    private boolean matches(Club c, String q) {
        return lower(c.getName()).contains(q)
                || lower(c.getDescription()).contains(q)
                || lower(c.getCategory()).contains(q);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private int emptyMessage() {
        boolean filtering = !query.trim().isEmpty() || category != null;
        if (filtering && !allClubs.isEmpty()) {
            return R.string.empty_clubs_filtered;
        }
        switch (mode) {
            case MODE_MINE:
                return R.string.empty_clubs_mine;
            case MODE_PENDING:
                return R.string.empty_clubs_pending;
            default:
                return offlineEmpty ? R.string.empty_clubs_offline : R.string.empty_clubs_all;
        }
    }

    private void showMessage(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}

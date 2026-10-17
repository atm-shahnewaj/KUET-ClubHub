package com.kuet.clubhub.ui;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.UserRepository;
import com.kuet.clubhub.databinding.ActivityMainBinding;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.clubs.ClubListFragment;
import com.kuet.clubhub.ui.events.EventsFragment;
import com.kuet.clubhub.ui.events.MyEventsFragment;
import com.kuet.clubhub.ui.profile.ProfileFragment;
import com.kuet.clubhub.util.ThemeManager;

/** Hosts the bottom navigation. The tabs shown depend on the signed-in user's role. */
public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private SessionManager session;
    private UserProfile profile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        profile = session.getProfile();
        if (profile == null || FirebaseAuth.getInstance().getCurrentUser() == null) {
            // No usable session: let the launcher sort it out.
            startClearTask(LauncherActivity.class);
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        binding.toolbar.inflateMenu(R.menu.main_toolbar);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_toggle_theme) {
                ThemeManager.toggle(this);
                return true;
            }
            return false;
        });

        binding.bottomNav.inflateMenu(menuFor(profile.getRoleEnum()));
        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item);
            return true;
        });
        binding.bottomNav.setOnItemReselectedListener(item -> { /* keep the current screen */ });

        if (savedInstanceState == null) {
            // The first tab is already selected after inflating the menu, so setSelectedItemId()
            // would only fire the "reselected" listener and nothing would be shown. Show it directly.
            MenuItem first = binding.bottomNav.getMenu().findItem(R.id.nav_clubs);
            first.setChecked(true);
            showTab(first);
        }

        refreshRoleFromServer();
    }

    private int menuFor(Role role) {
        switch (role) {
            case ADMIN:
                return R.menu.bottom_nav_admin;
            case ORGANIZER:
                return R.menu.bottom_nav_organizer;
            default:
                return R.menu.bottom_nav_student;
        }
    }

    private void showTab(MenuItem item) {
        int id = item.getItemId();
        Fragment fragment;
        if (id == R.id.nav_clubs) {
            fragment = ClubListFragment.newInstance(ClubListFragment.MODE_ALL);
        } else if (id == R.id.nav_events) {
            fragment = new EventsFragment();
        } else if (id == R.id.nav_my_events) {
            fragment = new MyEventsFragment();
        } else if (id == R.id.nav_my_clubs) {
            fragment = ClubListFragment.newInstance(ClubListFragment.MODE_MINE);
        } else if (id == R.id.nav_pending) {
            fragment = ClubListFragment.newInstance(ClubListFragment.MODE_PENDING);
        } else {
            fragment = new ProfileFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    /**
     * Re-reads the profile in the background so a role change made in the Firebase
     * console (for example promoting someone to admin) takes effect on the next launch.
     */
    private void refreshRoleFromServer() {
        UserRepository.fetchProfile(profile.getUid(), new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile fresh) {
                boolean roleChanged = !fresh.getRole().equals(profile.getRole());
                session.save(fresh);
                if (roleChanged && !isFinishing()) {
                    startClearTask(MainActivity.class);
                }
            }

            @Override
            public void onError(String message) {
                // Offline or transient problem: keep using the saved profile.
            }
        });
    }
}

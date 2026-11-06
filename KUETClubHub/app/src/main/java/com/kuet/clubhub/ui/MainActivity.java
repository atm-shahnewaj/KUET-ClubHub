package com.kuet.clubhub.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.ScheduleSync;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.UserRepository;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.data.local.ScheduledEventEntity;
import com.kuet.clubhub.databinding.ActivityMainBinding;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.notify.AlertController;
import com.kuet.clubhub.notify.AlertProcessor;
import com.kuet.clubhub.ui.alerts.AlertsActivity;
import com.kuet.clubhub.ui.clubs.ClubListFragment;
import com.kuet.clubhub.ui.events.EventDetailActivity;
import com.kuet.clubhub.ui.events.EventPassActivity;
import com.kuet.clubhub.ui.events.EventsFragment;
import com.kuet.clubhub.ui.home.HomeFragment;
import com.kuet.clubhub.ui.profile.ProfileFragment;
import com.kuet.clubhub.ui.scan.ScanActivity;
import com.kuet.clubhub.util.Io;
import com.kuet.clubhub.util.NotificationPrefs;
import com.kuet.clubhub.util.ThemeManager;
import com.kuet.clubhub.util.WidgetEventPicker;

import java.util.List;

/** Hosts the floating 4-tab bottom navigation (Home, Clubs, Cal, Profile) matching UI.jpg. */
public class MainActivity extends BaseActivity {

    public static final String ACTION_SHORTCUT_MY_PASS = "com.kuet.clubhub.action.SHORTCUT_MY_PASS";
    public static final String ACTION_SHORTCUT_SCAN_QR = "com.kuet.clubhub.action.SHORTCUT_SCAN_QR";
    public static final String ACTION_OPEN_EVENT = "com.kuet.clubhub.action.OPEN_EVENT";

    private ActivityMainBinding binding;
    private SessionManager session;
    private UserProfile profile;

    private ListenerRegistration registrationsListener;
    private BroadcastReceiver alertReceiver;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> AlertController.refresh(this));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        profile = session.getProfile();
        if (profile == null || FirebaseAuth.getInstance().getCurrentUser() == null) {
            startClearTask(LauncherActivity.class);
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets(binding.getRoot());
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!ThemeManager.isNight(this));

        binding.bottomNav.inflateMenu(menuFor(profile.getRoleEnum()));
        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item);
            return true;
        });
        binding.bottomNav.setOnItemReselectedListener(item -> { /* keep the current screen */ });

        if (savedInstanceState == null) {
            MenuItem first = binding.bottomNav.getMenu().findItem(R.id.nav_home);
            if (first == null) {
                first = binding.bottomNav.getMenu().getItem(0);
            }
            first.setChecked(true);
            showTab(first);
            askForNotificationPermission();
            handleLaunchAction(getIntent());
        }

        NotificationPrefs.ensureBaseline(this);
        refreshRoleFromServer();
    }

    public void switchToTab(int itemId) {
        if (binding == null) return;
        MenuItem item = binding.bottomNav.getMenu().findItem(itemId);
        if (item != null) {
            item.setChecked(true);
            showTab(item);
        }
    }

    public void openQuickShortcut(String action) {
        Intent i = new Intent(action);
        handleLaunchAction(i);
    }

    private void handleLaunchAction(@Nullable Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        final String action = intent.getAction();
        if (ACTION_OPEN_EVENT.equals(action)) {
            String eventId = intent.getStringExtra(EventDetailActivity.EXTRA_EVENT_ID);
            if (eventId != null && !eventId.isEmpty()) {
                Intent detail = new Intent(this, EventDetailActivity.class);
                detail.putExtra(EventDetailActivity.EXTRA_EVENT_ID, eventId);
                startActivity(detail);
            }
            return;
        }
        if (!ACTION_SHORTCUT_MY_PASS.equals(action) && !ACTION_SHORTCUT_SCAN_QR.equals(action)) {
            return;
        }
        final Context app = getApplicationContext();
        Io.DISK.execute(() -> {
            List<ScheduledEventEntity> rows = AppDatabase.getInstance(app).scheduleDao().getAll();
            final ScheduledEventEntity next =
                    WidgetEventPicker.pickNext(rows, System.currentTimeMillis());
            runOnUiThread(() -> {
                if (isDestroyed() || binding == null) {
                    return;
                }
                if (next == null) {
                    Snackbar.make(binding.getRoot(), R.string.msg_shortcut_no_upcoming,
                            Snackbar.LENGTH_LONG).show();
                    return;
                }
                if (ACTION_SHORTCUT_MY_PASS.equals(action)) {
                    Intent pass = new Intent(MainActivity.this, EventPassActivity.class);
                    pass.putExtra(EventPassActivity.EXTRA_EVENT_ID, next.eventId);
                    startActivity(pass);
                } else {
                    Intent scan = new Intent(MainActivity.this, ScanActivity.class);
                    scan.putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_STUDENT_CHECKIN);
                    scan.putExtra(ScanActivity.EXTRA_EVENT_ID, next.eventId);
                    scan.putExtra(ScanActivity.EXTRA_EVENT_TITLE,
                            next.title == null ? "" : next.title);
                    startActivity(scan);
                }
            });
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (binding == null) {
            return;
        }
        registrationsListener = EventRepository.getInstance(this).listenMyRegistrations(
                profile.getUid(), new EventRepository.MyRegistrationsListener() {
                    @Override
                    public void onChanged(List<MyRegistration> registrations) {
                        ScheduleSync.sync(MainActivity.this, registrations);
                    }

                    @Override
                    public void onError(String message) {
                    }
                });

        alertReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String title = intent.getStringExtra(AlertProcessor.EXTRA_TITLE);
                Snackbar.make(binding.getRoot(),
                                getString(R.string.snackbar_new_alert, title == null ? "" : title),
                                Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_view, v ->
                                startActivity(new Intent(MainActivity.this, AlertsActivity.class)))
                        .show();
            }
        };
        ContextCompat.registerReceiver(this, alertReceiver,
                new IntentFilter(AlertProcessor.ACTION_ALERTS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        AlertController.refresh(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registrationsListener != null) {
            registrationsListener.remove();
            registrationsListener = null;
        }
        if (alertReceiver != null) {
            unregisterReceiver(alertReceiver);
            alertReceiver = null;
        }
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
                || NotificationPrefs.askedPermission(this)) {
            return;
        }
        NotificationPrefs.setAskedPermission(this);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_notif_title)
                .setMessage(R.string.dialog_notif_message)
                .setNegativeButton(R.string.action_not_now, null)
                .setPositiveButton(R.string.action_allow, (d, w) ->
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS))
                .show();
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
        if (id == R.id.nav_home) {
            fragment = new HomeFragment();
        } else if (id == R.id.nav_clubs) {
            fragment = ClubListFragment.newInstance(ClubListFragment.MODE_ALL);
        } else if (id == R.id.nav_events) {
            fragment = new EventsFragment();
        } else {
            fragment = new ProfileFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void refreshRoleFromServer() {
        UserRepository.fetchProfile(profile.getUid(), new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile fresh) {
                boolean roleChanged = !fresh.getRole().equals(profile.getRole());
                session.save(fresh);
                if (roleChanged && !isFinishing()) {
                    startClearTask(MainActivity.class);
                } else {
                    AlertController.refresh(MainActivity.this);
                }
            }

            @Override
            public void onError(String message) {
            }
        });
    }
}

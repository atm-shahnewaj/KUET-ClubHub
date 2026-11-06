package com.kuet.clubhub.ui.profile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.kuet.clubhub.R;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.UserRepository;
import com.kuet.clubhub.databinding.FragmentProfileBinding;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.notify.AlertController;
import com.kuet.clubhub.notify.AlertProcessor;
import com.kuet.clubhub.notify.NotificationHelper;
import com.kuet.clubhub.notify.ReminderScheduler;
import com.kuet.clubhub.ui.auth.LoginActivity;
import com.kuet.clubhub.ui.certificate.VerifyCertificateActivity;
import com.kuet.clubhub.util.Io;
import com.kuet.clubhub.util.NotificationPrefs;
import com.kuet.clubhub.util.ThemeManager;
import com.kuet.clubhub.util.Years;

import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnEditProfile.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EditProfileActivity.class)));

        binding.btnVerifyCert.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), VerifyCertificateActivity.class)));

        setupNotificationControls();
        setupThemeToggle();

        binding.btnLogout.setOnClickListener(v -> {
            UserRepository.logout(requireContext());
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }

    /** Re-read the profile every time the screen comes back (the edit screen may have changed it). */
    @Override
    public void onStart() {
        super.onStart();
        showProfile();
        updateNotificationStatus();
    }

    private void showProfile() {
        UserProfile p = new SessionManager(requireContext()).getProfile();
        if (p == null || binding == null) {
            return;
        }
        String name = p.getName() == null ? "" : p.getName();
        binding.tvAvatar.setText(name.isEmpty() ? "?"
                : name.substring(0, 1).toUpperCase(Locale.ROOT));
        com.kuet.clubhub.util.ImageHelper.bindUserAvatar(binding.ivAvatar, binding.tvAvatar, p.getPhotoBase64(), name);
        binding.cardProfileAvatar.setOnClickListener(v -> startActivity(new Intent(requireContext(), EditProfileActivity.class)));
        binding.tvName.setText(name);
        binding.tvRole.setText(p.getRoleEnum().getLabel());
        binding.tvEmail.setText(p.getEmail());
        binding.tvStudentId.setText(p.getStudentId());
        binding.tvDepartment.setText(p.getDepartment());
        binding.tvYear.setText(Years.label(p.getYear()));

        fillChips(binding.chipsInterests, p.getInterests(), binding.tvNoInterests);
        fillChips(binding.chipsSkills, p.getSkills(), binding.tvNoSkills);
    }

    private void fillChips(ChipGroup group, List<String> tags, View emptyHint) {
        group.removeAllViews();
        boolean any = tags != null && !tags.isEmpty();
        emptyHint.setVisibility(any ? View.GONE : View.VISIBLE);
        if (!any) {
            return;
        }
        for (String tag : tags) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, group, false);
            chip.setText(tag);
            chip.setCheckable(false);
            chip.setClickable(false);
            group.addView(chip);
        }
    }

    // ------------------------------------------------------------------ notifications

    private void setupNotificationControls() {
        binding.switchReminders.setChecked(NotificationPrefs.remindersEnabled(requireContext()));
        binding.switchAlerts.setChecked(NotificationPrefs.alertsEnabled(requireContext()));

        binding.switchReminders.setOnCheckedChangeListener((button, checked) -> {
            NotificationPrefs.setRemindersEnabled(requireContext(), checked);
            // Sets the alarms again, or cancels them all when reminders are off.
            final android.content.Context app = requireContext().getApplicationContext();
            Io.DISK.execute(() -> ReminderScheduler.rescheduleAll(app));
        });

        binding.switchAlerts.setOnCheckedChangeListener((button, checked) -> {
            NotificationPrefs.setAlertsEnabled(requireContext(), checked);
            if (checked) {
                NotificationPrefs.resetBaseline(requireContext());
            }
            AlertController.refresh(requireContext());
        });

        binding.btnTestReminder.setOnClickListener(v -> {
            ReminderScheduler.scheduleTest(requireContext(), 10_000);
            Snackbar.make(binding.getRoot(), R.string.msg_test_reminder_scheduled,
                    Snackbar.LENGTH_LONG).show();
        });

        binding.btnTestAlert.setOnClickListener(v -> {
            AlertProcessor.postTestAlert(requireContext());
            Snackbar.make(binding.getRoot(), R.string.msg_test_alert_sent,
                    Snackbar.LENGTH_SHORT).show();
        });

        binding.btnBattery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException e) {
                Snackbar.make(binding.getRoot(), R.string.msg_no_battery_settings,
                        Snackbar.LENGTH_LONG).show();
            }
        });

        binding.tvNotifStatus.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            startActivity(intent);
        });
    }

    private void updateNotificationStatus() {
        if (binding == null) {
            return;
        }
        binding.tvNotifStatus.setVisibility(
                NotificationHelper.canNotify(requireContext()) ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ appearance

    private void setupThemeToggle() {
        int mode = ThemeManager.getMode(requireContext());
        int checkedId = mode == AppCompatDelegate.MODE_NIGHT_NO ? R.id.btn_theme_light
                : mode == AppCompatDelegate.MODE_NIGHT_YES ? R.id.btn_theme_dark
                : R.id.btn_theme_system;
        binding.toggleTheme.check(checkedId);
        binding.toggleTheme.addOnButtonCheckedListener((group, id, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int newMode = id == R.id.btn_theme_light ? AppCompatDelegate.MODE_NIGHT_NO
                    : id == R.id.btn_theme_dark ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (newMode != ThemeManager.getMode(requireContext())) {
                ThemeManager.setMode(requireContext(), newMode);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

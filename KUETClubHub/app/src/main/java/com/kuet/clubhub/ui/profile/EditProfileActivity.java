package com.kuet.clubhub.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.UserRepository;
import com.kuet.clubhub.databinding.ActivityEditProfileBinding;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.notify.AlertController;
import com.kuet.clubhub.util.ImageHelper;
import com.kuet.clubhub.util.Interests;
import com.kuet.clubhub.util.Io;
import com.kuet.clubhub.util.NotificationPrefs;
import com.kuet.clubhub.util.PickerUtil;
import com.kuet.clubhub.util.Years;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Edit profile picture, name, department, year, interest tags and skills. */
public class EditProfileActivity extends BaseActivity {

    private static final int CHIP_ID_BASE = 3000;
    private static final int MAX_TAGS = 15;
    private static final String S_PHOTO = "s_photo";

    private ActivityEditProfileBinding binding;
    private SessionManager session;
    private UserProfile profile;
    private String photoBase64;

    private final ActivityResultLauncher<String> pickPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                Io.DISK.execute(() -> {
                    String encoded = ImageHelper.encodeUriToBase64(this, uri, 256, 256, 80);
                    runOnUiThread(() -> {
                        if (encoded != null && binding != null) {
                            photoBase64 = encoded;
                            ImageHelper.bindUserAvatar(binding.ivProfilePhoto, null, photoBase64, profile.getName());
                        }
                    });
                });
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        session = new SessionManager(this);
        profile = session.getProfile();
        if (profile == null) {
            finish();
            return;
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        photoBase64 = savedInstanceState != null
                ? savedInstanceState.getString(S_PHOTO, profile.getPhotoBase64())
                : profile.getPhotoBase64();
        ImageHelper.bindUserAvatar(binding.ivProfilePhoto, null, photoBase64, profile.getName());

        View.OnClickListener pickPhoto = v -> pickPhotoLauncher.launch("image/*");
        binding.cardPickPhoto.setOnClickListener(pickPhoto);
        binding.btnChangePhoto.setOnClickListener(pickPhoto);

        List<String> mine = profile.getInterests() == null
                ? new ArrayList<String>() : profile.getInterests();
        List<String> suggested = Arrays.asList(Interests.SUGGESTED);
        for (int i = 0; i < Interests.SUGGESTED.length; i++) {
            Chip chip = (Chip) getLayoutInflater()
                    .inflate(R.layout.item_filter_chip, binding.chipGroupInterests, false);
            chip.setId(CHIP_ID_BASE + i);
            chip.setText(Interests.SUGGESTED[i]);
            chip.setChecked(savedInstanceState == null && mine.contains(Interests.SUGGESTED[i]));
            binding.chipGroupInterests.addView(chip);
        }

        if (savedInstanceState == null) {
            binding.etName.setText(profile.getName());
            binding.etDepartment.setText(profile.getDepartment());
            binding.etYear.setText(profile.getYear() == 0 ? "" : Years.label(profile.getYear()));
            List<String> others = new ArrayList<>();
            for (String tag : mine) {
                if (!suggested.contains(tag)) {
                    others.add(tag);
                }
            }
            binding.etOtherInterests.setText(Interests.join(others));
            binding.etSkills.setText(Interests.join(profile.getSkills()));
        }

        View.OnClickListener pickYear = v -> PickerUtil.showChoice(this,
                getString(R.string.title_pick_year), Years.LABELS, textOf(binding.etYear),
                picked -> binding.etYear.setText(picked));
        binding.etYear.setOnClickListener(pickYear);
        binding.tilYear.setEndIconOnClickListener(pickYear);

        binding.btnSave.setOnClickListener(v -> save());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(S_PHOTO, photoBase64);
    }

    private void save() {
        String name = textOf(binding.etName);
        String department = textOf(binding.etDepartment);
        int year = Years.fromLabel(textOf(binding.etYear));

        binding.tilName.setError(null);
        binding.tilDepartment.setError(null);
        binding.tilYear.setError(null);

        boolean valid = true;
        if (name.length() < 2) {
            binding.tilName.setError(getString(R.string.error_name_required));
            valid = false;
        }
        if (department.isEmpty()) {
            binding.tilDepartment.setError(getString(R.string.error_department_required));
            valid = false;
        }
        if (year == 0) {
            binding.tilYear.setError(getString(R.string.error_year_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        Set<String> interestSet = new LinkedHashSet<>();
        for (int i = 0; i < Interests.SUGGESTED.length; i++) {
            Chip chip = binding.chipGroupInterests.findViewById(CHIP_ID_BASE + i);
            if (chip != null && chip.isChecked()) {
                interestSet.add(Interests.SUGGESTED[i]);
            }
        }
        interestSet.addAll(Interests.parse(textOf(binding.etOtherInterests), MAX_TAGS));
        final List<String> interests = new ArrayList<>(interestSet);
        final List<String> skills = Interests.parse(textOf(binding.etSkills), MAX_TAGS);

        final boolean interestsChanged = !new LinkedHashSet<>(interests)
                .equals(new LinkedHashSet<>(profile.getInterests()));

        setLoading(true);
        UserRepository.updateProfile(profile.getUid(), name, department, year, skills, interests,
                photoBase64, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        profile.setName(name);
                        profile.setDepartment(department);
                        profile.setYear(year);
                        profile.setSkills(skills);
                        profile.setInterests(interests);
                        profile.setPhotoBase64(photoBase64);
                        com.kuet.clubhub.util.UserPhotos.put(profile.getUid(), photoBase64);
                        session.save(profile);
                        if (interestsChanged) {
                            NotificationPrefs.resetBaseline(EditProfileActivity.this);
                        }
                        AlertController.refresh(EditProfileActivity.this);
                        Toast.makeText(EditProfileActivity.this, R.string.msg_profile_saved,
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
    }

    private static String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}

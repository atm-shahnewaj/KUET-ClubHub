package com.kuet.clubhub.ui.clubs;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.ClubRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityClubEditBinding;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.Categories;
import com.kuet.clubhub.util.ImageHelper;
import com.kuet.clubhub.util.Io;
import com.kuet.clubhub.util.PickerUtil;

import java.util.Arrays;

/** Creates a club (no club id extra) or edits an existing one (club id + current values + optional logo). */
public class ClubEditActivity extends BaseActivity {

    public static final String EXTRA_CLUB_ID = "club_id";
    public static final String EXTRA_NAME = "club_name";
    public static final String EXTRA_CATEGORY = "club_category";
    public static final String EXTRA_DESCRIPTION = "club_description";
    public static final String EXTRA_LOGO = "club_logo";

    private static final String S_LOGO = "s_logo";

    private ActivityClubEditBinding binding;
    private ClubRepository repository;
    private UserProfile profile;
    private String clubId;
    private String logoBase64;

    private final ActivityResultLauncher<String> pickLogoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                Io.DISK.execute(() -> {
                    String encoded = ImageHelper.encodeUriToBase64(this, uri, 256, 256, 80);
                    runOnUiThread(() -> {
                        if (encoded != null && binding != null) {
                            logoBase64 = encoded;
                            ImageHelper.bindClubLogo(binding.ivClubLogo, logoBase64);
                        }
                    });
                });
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClubEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        profile = new SessionManager(this).getProfile();
        if (profile == null || profile.getRoleEnum() == Role.STUDENT) {
            finish();
            return;
        }
        repository = ClubRepository.getInstance(this);
        clubId = getIntent().getStringExtra(EXTRA_CLUB_ID);
        boolean editing = clubId != null;

        binding.toolbar.setTitle(editing ? R.string.title_edit_club : R.string.title_new_club);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        logoBase64 = savedInstanceState != null
                ? savedInstanceState.getString(S_LOGO)
                : getIntent().getStringExtra(EXTRA_LOGO);
        ImageHelper.bindClubLogo(binding.ivClubLogo, logoBase64);

        View.OnClickListener pickLogo = v -> pickLogoLauncher.launch("image/*");
        binding.cardPickLogo.setOnClickListener(pickLogo);
        binding.btnUploadLogo.setOnClickListener(pickLogo);

        if (editing && savedInstanceState == null) {
            binding.etName.setText(getIntent().getStringExtra(EXTRA_NAME));
            binding.etCategory.setText(getIntent().getStringExtra(EXTRA_CATEGORY));
            binding.etDescription.setText(getIntent().getStringExtra(EXTRA_DESCRIPTION));
        }

        View.OnClickListener pickCategory = v -> PickerUtil.showChoice(
                this,
                getString(R.string.title_pick_category),
                Categories.ALL,
                textOf(binding.etCategory),
                picked -> binding.etCategory.setText(picked));
        binding.etCategory.setOnClickListener(pickCategory);
        binding.tilCategory.setEndIconOnClickListener(pickCategory);

        binding.btnSave.setOnClickListener(v -> save());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(S_LOGO, logoBase64);
    }

    private void save() {
        String name = textOf(binding.etName);
        String category = textOf(binding.etCategory);
        String description = textOf(binding.etDescription);

        binding.tilName.setError(null);
        binding.tilCategory.setError(null);
        binding.tilDescription.setError(null);

        boolean valid = true;
        if (name.length() < 3) {
            binding.tilName.setError(getString(R.string.error_club_name_short));
            valid = false;
        }
        if (!Arrays.asList(Categories.ALL).contains(category)) {
            binding.tilCategory.setError(getString(R.string.error_category_required));
            valid = false;
        }
        if (description.isEmpty()) {
            binding.tilDescription.setError(getString(R.string.error_description_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        if (clubId != null) {
            repository.updateClub(clubId, name, category, description, logoBase64, new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(ClubEditActivity.this,
                            R.string.msg_club_updated, Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        } else {
            boolean admin = profile.getRoleEnum() == Role.ADMIN;
            repository.createClub(name, category, description, logoBase64, profile.getUid(), admin,
                    new Callback<String>() {
                        @Override
                        public void onSuccess(String newId) {
                            Toast.makeText(ClubEditActivity.this,
                                    admin ? R.string.msg_club_created : R.string.msg_club_submitted,
                                    Toast.LENGTH_LONG).show();
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

    private static String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}

package com.kuet.clubhub.ui.clubs;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.ClubRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityClubDetailBinding;
import com.kuet.clubhub.model.Club;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;

import java.util.Locale;

public class ClubDetailActivity extends BaseActivity {

    public static final String EXTRA_CLUB_ID = "club_id";

    private ActivityClubDetailBinding binding;
    private ClubRepository repository;
    private UserProfile profile;
    private String clubId;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClubDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets(binding.getRoot());

        clubId = getIntent().getStringExtra(EXTRA_CLUB_ID);
        profile = new SessionManager(this).getProfile();
        if (clubId == null || profile == null) {
            finish();
            return;
        }
        repository = ClubRepository.getInstance(this);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (clubId == null || profile == null) {
            return;
        }
        registration = repository.listenClub(clubId, new ClubRepository.ClubListener() {
            @Override
            public void onClub(Club club) {
                bind(club);
            }

            @Override
            public void onMissing() {
                Toast.makeText(ClubDetailActivity.this,
                        R.string.msg_club_missing, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private void bind(Club club) {
        String name = club.getName() == null ? "" : club.getName();
        binding.tvAvatar.setText(name.isEmpty() ? "?"
                : name.substring(0, 1).toUpperCase(Locale.ROOT));
        binding.tvName.setText(name);
        binding.tvCategory.setText(club.getCategory());
        int members = (int) club.getMemberCount();
        binding.tvMembers.setText(getResources()
                .getQuantityString(R.plurals.member_count, members, members));
        binding.tvDescription.setText(club.getDescription());
        binding.tvPending.setVisibility(club.isApproved() ? View.GONE : View.VISIBLE);

        Role role = profile.getRoleEnum();
        boolean isAdmin = role == Role.ADMIN;
        boolean isOwner = role == Role.ORGANIZER && club.hasOrganizer(profile.getUid());

        binding.btnApprove.setVisibility(isAdmin && !club.isApproved() ? View.VISIBLE : View.GONE);
        binding.btnApprove.setOnClickListener(v -> approve());

        binding.btnEdit.setVisibility(isAdmin || isOwner ? View.VISIBLE : View.GONE);
        binding.btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, ClubEditActivity.class);
            intent.putExtra(ClubEditActivity.EXTRA_CLUB_ID, club.getId());
            intent.putExtra(ClubEditActivity.EXTRA_NAME, club.getName());
            intent.putExtra(ClubEditActivity.EXTRA_CATEGORY, club.getCategory());
            intent.putExtra(ClubEditActivity.EXTRA_DESCRIPTION, club.getDescription());
            startActivity(intent);
        });
    }

    private void approve() {
        binding.btnApprove.setEnabled(false);
        repository.approveClub(clubId, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                binding.btnApprove.setEnabled(true);
                Snackbar.make(binding.getRoot(), R.string.msg_club_approved,
                        Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                binding.btnApprove.setEnabled(true);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}

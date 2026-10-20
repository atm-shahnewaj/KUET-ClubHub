package com.kuet.clubhub.ui.clubs;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.ClubRepository;
import com.kuet.clubhub.data.MembershipRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityClubDetailBinding;
import com.kuet.clubhub.model.Club;
import com.kuet.clubhub.model.ClubMember;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.databinding.DialogTextInputBinding;

import java.util.Locale;

public class ClubDetailActivity extends BaseActivity {

    public static final String EXTRA_CLUB_ID = "club_id";

    private ActivityClubDetailBinding binding;
    private ClubRepository repository;
    private UserProfile profile;
    private String clubId;
    private ListenerRegistration registration;
    private ListenerRegistration membershipRegistration;
    private Club currentClub;
    private ClubMember myMembership;
    private boolean membershipLoaded = false;
    private boolean busy = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClubDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

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
        // Only people who can apply need their own membership record.
        if (profile.getRoleEnum() != Role.ADMIN) {
            membershipRegistration = MembershipRepository.listenMine(clubId, profile.getUid(),
                    new MembershipRepository.MyMembershipListener() {
                        @Override
                        public void onChanged(@Nullable ClubMember member) {
                            myMembership = member;
                            membershipLoaded = true;
                            renderMembership();
                        }

                        @Override
                        public void onError(String message) {
                            // The join button is a nicety; stay quiet while offline.
                        }
                    });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        if (membershipRegistration != null) {
            membershipRegistration.remove();
            membershipRegistration = null;
        }
    }

    private void bind(Club club) {
        currentClub = club;
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

        binding.btnMembers.setVisibility((isAdmin || isOwner) && club.isApproved()
                ? View.VISIBLE : View.GONE);
        binding.btnMembers.setOnClickListener(v -> {
            Intent intent = new Intent(this, ClubMembersActivity.class);
            intent.putExtra(ClubMembersActivity.EXTRA_CLUB_ID, club.getId());
            intent.putExtra(ClubMembersActivity.EXTRA_CLUB_NAME, club.getName());
            startActivity(intent);
        });
        renderMembership();

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

    // ------------------------------------------------------------------ membership

    /** Join / leave controls for students (and organizers of other clubs). */
    private void renderMembership() {
        binding.tvMembership.setVisibility(View.GONE);
        binding.btnJoin.setVisibility(View.GONE);
        binding.btnLeaveClub.setVisibility(View.GONE);

        Role role = profile.getRoleEnum();
        boolean owner = currentClub != null && currentClub.hasOrganizer(profile.getUid());
        if (currentClub == null || !membershipLoaded || role == Role.ADMIN || owner
                || !currentClub.isApproved()) {
            return;
        }
        String status = myMembership == null ? null : myMembership.getStatus();
        if (ClubMember.APPROVED.equals(status)) {
            binding.tvMembership.setVisibility(View.VISIBLE);
            binding.tvMembership.setText(R.string.membership_member);
            binding.btnLeaveClub.setVisibility(View.VISIBLE);
            binding.btnLeaveClub.setText(R.string.action_leave_club);
        } else if (ClubMember.PENDING.equals(status)) {
            binding.tvMembership.setVisibility(View.VISIBLE);
            binding.tvMembership.setText(R.string.membership_pending);
            binding.btnLeaveClub.setVisibility(View.VISIBLE);
            binding.btnLeaveClub.setText(R.string.action_withdraw_application);
        } else {
            if (ClubMember.REJECTED.equals(status)) {
                binding.tvMembership.setVisibility(View.VISIBLE);
                binding.tvMembership.setText(R.string.membership_rejected);
            }
            binding.btnJoin.setVisibility(View.VISIBLE);
            binding.btnJoin.setText(ClubMember.REJECTED.equals(status)
                    ? R.string.action_apply_again : R.string.action_join_club);
        }
        binding.btnJoin.setEnabled(!busy);
        binding.btnLeaveClub.setEnabled(!busy);
        binding.btnJoin.setOnClickListener(v -> askForMessageAndApply());
        binding.btnLeaveClub.setOnClickListener(v -> confirmLeave());
    }

    private void askForMessageAndApply() {
        final DialogTextInputBinding input = DialogTextInputBinding.inflate(getLayoutInflater());
        input.tilText.setHint(getString(R.string.hint_application_message));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_apply_title)
                .setView(input.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_send_application, (d, w) -> {
                    setBusy(true);
                    String message = input.etText.getText() == null ? ""
                            : input.etText.getText().toString().trim();
                    MembershipRepository.apply(clubId, profile, message, new Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            setBusy(false);
                            Snackbar.make(binding.getRoot(), R.string.msg_application_sent,
                                    Snackbar.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String message) {
                            setBusy(false);
                            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }

    private void confirmLeave() {
        final boolean member = myMembership != null
                && ClubMember.APPROVED.equals(myMembership.getStatus());
        new MaterialAlertDialogBuilder(this)
                .setTitle(member ? R.string.dialog_leave_club_title
                        : R.string.dialog_withdraw_title)
                .setMessage(member ? R.string.dialog_leave_club_message
                        : R.string.dialog_withdraw_message)
                .setNegativeButton(R.string.action_keep, null)
                .setPositiveButton(R.string.action_confirm_yes, (d, w) -> {
                    setBusy(true);
                    MembershipRepository.leave(clubId, profile.getUid(), member,
                            new Callback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    setBusy(false);
                                }

                                @Override
                                public void onError(String message) {
                                    setBusy(false);
                                    Snackbar.make(binding.getRoot(), message,
                                            Snackbar.LENGTH_LONG).show();
                                }
                            });
                })
                .show();
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.btnJoin.setEnabled(!value);
        binding.btnLeaveClub.setEnabled(!value);
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

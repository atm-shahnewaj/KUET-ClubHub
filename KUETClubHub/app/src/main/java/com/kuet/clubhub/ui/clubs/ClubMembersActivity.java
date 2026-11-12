package com.kuet.clubhub.ui.clubs;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.MembershipRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityListBinding;
import com.kuet.clubhub.model.ClubMember;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.notify.ToastCenter;
import com.kuet.clubhub.ui.common.RowAdapter;
import com.kuet.clubhub.util.Years;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Organizer screen: approve or reject membership applications, and see or remove members. */
public class ClubMembersActivity extends BaseActivity implements RowAdapter.Listener {

    public static final String EXTRA_CLUB_ID = "club_id";
    public static final String EXTRA_CLUB_NAME = "club_name";

    private ActivityListBinding binding;
    private RowAdapter adapter;
    private UserProfile profile;
    private String clubId;
    private String clubName = "";

    private List<ClubMember> all = new ArrayList<>();
    private final Set<String> seenPendingUids = new HashSet<>();
    private boolean pendingBaselineSet = false;
    private boolean showPending = true;
    private boolean loaded = false;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        clubId = getIntent().getStringExtra(EXTRA_CLUB_ID);
        profile = new SessionManager(this).getProfile();
        if (clubId == null || profile == null) {
            finish();
            return;
        }
        String cName = getIntent().getStringExtra(EXTRA_CLUB_NAME);
        clubName = cName == null ? "" : cName;
        binding.toolbar.setTitle(R.string.title_membership);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.tvTitle.setText(clubName);

        adapter = new RowAdapter(this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.toggle.setVisibility(View.VISIBLE);
        binding.toggle.check(R.id.tab_1);
        binding.toggle.addOnButtonCheckedListener((group, id, isChecked) -> {
            if (isChecked) {
                showPending = id == R.id.tab_1;
                render();
            }
        });
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (clubId == null || profile == null) {
            return;
        }
        registration = MembershipRepository.listenAll(clubId,
                new MembershipRepository.MembersListener() {
                    @Override
                    public void onMembers(List<ClubMember> members) {
                        if (!pendingBaselineSet) {
                            pendingBaselineSet = true;
                            for (ClubMember m : members) {
                                if (m.getUid() != null && ClubMember.PENDING.equals(m.getStatus())) {
                                    seenPendingUids.add(m.getUid());
                                }
                            }
                        } else {
                            for (ClubMember m : members) {
                                if (m.getUid() != null && ClubMember.PENDING.equals(m.getStatus())
                                        && seenPendingUids.add(m.getUid())) {
                                    ToastCenter.newClubJoinRequest(ClubMembersActivity.this,
                                            m.getName(), clubName, clubId, m.getUid());
                                }
                            }
                        }
                        all = members;
                        loaded = true;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        loaded = true;
                        render();
                        showError(message);
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

    private void render() {
        List<ClubMember> pending = new ArrayList<>();
        List<ClubMember> members = new ArrayList<>();
        for (ClubMember m : all) {
            if (ClubMember.PENDING.equals(m.getStatus())) {
                pending.add(m);
            } else if (ClubMember.APPROVED.equals(m.getStatus())) {
                members.add(m);
            }
        }
        Collections.sort(pending, (a, b) -> time(a.getAppliedAt()).compareTo(time(b.getAppliedAt())));
        Collections.sort(members, (a, b) -> lower(a.getName()).compareTo(lower(b.getName())));

        binding.tab1.setText(getString(R.string.tab_pending_count, pending.size()));
        binding.tab2.setText(getString(R.string.tab_members_count, members.size()));
        binding.tvSubtitle.setText(getString(R.string.members_summary, pending.size(), members.size()));

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (ClubMember m : showPending ? pending : members) {
            String details = m.getStudentId() + "  •  " + m.getDepartment()
                    + "  •  " + Years.label(m.getYear());
            if (showPending && m.getMessage() != null && !m.getMessage().trim().isEmpty()) {
                details += "\n“" + m.getMessage().trim() + "”";
            }
            RowAdapter.Row row = RowAdapter.Row.item(m.getUid())
                    .avatar(RowAdapter.Row.initial(m.getName()))
                    .uid(m.getUid())
                    .title(m.getName())
                    .subtitle(details)
                    .tag(m);
            if (showPending) {
                row.primary(getString(R.string.action_approve)).secondary(getString(R.string.action_reject));
            } else {
                row.primary(getString(R.string.action_remove));
            }
            rows.add(row);
        }
        adapter.submitList(rows);

        binding.progress.setVisibility(!loaded && rows.isEmpty() ? View.VISIBLE : View.GONE);
        boolean showEmpty = loaded && rows.isEmpty();
        binding.emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        binding.emptyState.setText(showPending ? R.string.empty_pending_applications
                : R.string.empty_members);
    }

    // ------------------------------------------------------------------ row actions

    @Override
    public void onRowClick(RowAdapter.Row row) {
        // Rows are not tappable; the buttons do the work.
    }

    @Override
    public void onPrimary(RowAdapter.Row row) {
        ClubMember m = (ClubMember) row.tag;
        if (showPending) {
            decide(m, true);
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_remove_member_title)
                    .setMessage(getString(R.string.dialog_remove_member_message, m.getName()))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_remove, (d, w) ->
                            MembershipRepository.remove(clubId, m.getUid(), true, new Callback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    ToastCenter.showShort(ClubMembersActivity.this,
                                            m.getName() + " removed from club.");
                                }

                                @Override
                                public void onError(String message) {
                                    showError(message);
                                }
                            }))
                    .show();
        }
    }

    @Override
    public void onSecondary(RowAdapter.Row row) {
        decide((ClubMember) row.tag, false);
    }

    private void decide(ClubMember m, boolean approve) {
        MembershipRepository.decide(clubId, m.getUid(), approve, profile.getUid(),
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        String msg = getString(approve ? R.string.msg_member_approved
                                : R.string.msg_member_rejected, m.getName());
                        ToastCenter.showShort(ClubMembersActivity.this, msg);
                        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
    }

    private void showError(String message) {
        ToastCenter.show(this, message);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private static Date time(@Nullable Date d) {
        return d == null ? new Date(Long.MAX_VALUE) : d;
    }

    private static String lower(@Nullable String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}

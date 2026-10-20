package com.kuet.clubhub.ui.teams;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.TeamRepository;
import com.kuet.clubhub.databinding.ActivityListBinding;
import com.kuet.clubhub.databinding.DialogTeamInviteBinding;
import com.kuet.clubhub.model.Seeker;
import com.kuet.clubhub.model.Team;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.common.RowAdapter;
import com.kuet.clubhub.util.Interests;
import com.kuet.clubhub.util.QrCodeGenerator;
import com.kuet.clubhub.util.QrPayload;
import com.kuet.clubhub.util.TeamMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One team: its members, what it is looking for, the invite code (members only) and, for the
 * leader, suggested teammates taken from the board and ranked by the skill matcher.
 */
public class TeamDetailActivity extends BaseActivity implements RowAdapter.Listener {

    public static final String EXTRA_TEAM_ID = "team_id";

    private static final int MAX_SUGGESTIONS = 5;

    private ActivityListBinding binding;
    private RowAdapter adapter;
    private UserProfile profile;
    private String teamId;

    private Team team;
    private String myTeamId;
    private List<Seeker> seekers = new ArrayList<>();
    private boolean loaded = false;

    private ListenerRegistration teamReg;
    private ListenerRegistration myTeamReg;
    private ListenerRegistration seekersReg;
    private String seekersEventId;

    private AlertDialog inviteDialog;
    private String pendingInviteMessage;

    /** Result of the system contact picker (no permission is needed for a single picked number). */
    private final ActivityResultLauncher<Intent> contactLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getData() != null) {
                    sendSms(result.getData().getData());
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        teamId = getIntent().getStringExtra(EXTRA_TEAM_ID);
        profile = new SessionManager(this).getProfile();
        if (teamId == null || profile == null) {
            finish();
            return;
        }
        binding.toolbar.setTitle(R.string.title_team);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new RowAdapter(this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        binding.progress.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (teamId == null || profile == null) {
            return;
        }
        teamReg = TeamRepository.listenTeam(teamId, new TeamRepository.TeamListener() {
            @Override
            public void onTeam(Team t) {
                team = t;
                loaded = true;
                watchOthers(t);
                render();
            }

            @Override
            public void onMissing() {
                // Disbanded by the leader.
                Toast.makeText(TeamDetailActivity.this, R.string.msg_team_gone, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                loaded = true;
                render();
                showError(message);
            }
        });
    }

    /** Once the event is known: whether I am in a team there, and the board (for the leader). */
    private void watchOthers(Team t) {
        if (myTeamReg == null) {
            myTeamReg = TeamRepository.listenMyTeam(t.getEventId(), profile.getUid(),
                    new TeamRepository.MyTeamListener() {
                        @Override
                        public void onChanged(@Nullable String id) {
                            myTeamId = id;
                            render();
                        }

                        @Override
                        public void onError(String message) {
                            showError(message);
                        }
                    });
        }
        boolean iLead = profile.getUid().equals(t.getLeaderUid());
        if (iLead && seekersReg == null) {
            seekersEventId = t.getEventId();
            seekersReg = TeamRepository.listenSeekers(t.getEventId(), new TeamRepository.SeekersListener() {
                @Override
                public void onSeekers(List<Seeker> list) {
                    seekers = list;
                    render();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (teamReg != null) {
            teamReg.remove();
            teamReg = null;
        }
        if (myTeamReg != null) {
            myTeamReg.remove();
            myTeamReg = null;
        }
        if (seekersReg != null) {
            seekersReg.remove();
            seekersReg = null;
        }
        if (inviteDialog != null) {
            inviteDialog.dismiss();
            inviteDialog = null;
        }
    }

    // ------------------------------------------------------------------ rendering

    private boolean isLeader() {
        return team != null && profile.getUid().equals(team.getLeaderUid());
    }

    private boolean isMember() {
        return team != null && team.hasMember(profile.getUid());
    }

    private void render() {
        if (binding == null) {
            return;
        }
        binding.progress.setVisibility(loaded ? View.GONE : View.VISIBLE);
        if (team == null) {
            adapter.submitList(new ArrayList<RowAdapter.Row>());
            return;
        }
        binding.tvTitle.setText(team.getName());
        binding.tvSubtitle.setText(getString(R.string.team_detail_subtitle, team.getEventTitle(),
                team.size(), team.getMaxSize()));
        renderActions();

        List<RowAdapter.Row> rows = new ArrayList<>();

        // Status
        int missing = Math.max(0, team.getMinSize() - team.size());
        boolean ready = team.complete();
        rows.add(RowAdapter.Row.item("status")
                .avatar(ready ? "✓" : "…")
                .title(getString(ready ? R.string.team_status_ready : R.string.team_status_needs_more,
                        missing))
                .subtitle(getString(R.string.team_status_detail, team.getMinSize(), team.getMaxSize(),
                        getString(team.isOpen() ? R.string.pill_open : R.string.pill_invite_only))));

        // Members
        rows.add(RowAdapter.Row.header(getString(R.string.header_members, team.size(), team.getMaxSize())));
        for (String uid : team.getMemberUids()) {
            List<String> skills = team.getMemberSkills() == null ? null : team.getMemberSkills().get(uid);
            String name = team.nameOf(uid);
            boolean leader = uid.equals(team.getLeaderUid());
            RowAdapter.Row row = RowAdapter.Row.item("m:" + uid)
                    .avatar(RowAdapter.Row.initial(name))
                    .title(name)
                    .subtitle(skills == null || skills.isEmpty()
                            ? getString(R.string.no_skills_listed) : Interests.join(skills))
                    .tag(uid);
            if (leader) {
                row.pill(getString(R.string.pill_leader), RowAdapter.PILL_GREEN);
            } else if (isLeader()) {
                row.primary(getString(R.string.action_remove));
            }
            rows.add(row);
        }

        // What the team is looking for
        rows.add(RowAdapter.Row.header(getString(R.string.header_looking_for)));
        String note = team.getNote() == null ? "" : team.getNote().trim();
        rows.add(RowAdapter.Row.item("needs")
                .avatar("?")
                .title(team.getSkillsNeeded().isEmpty() ? getString(R.string.team_needs_nothing)
                        : Interests.join(team.getSkillsNeeded()))
                .subtitle(note));

        // Suggestions (leader only, while there is room)
        if (isLeader() && !team.full()) {
            rows.add(RowAdapter.Row.header(getString(R.string.header_suggested)));
            List<RowAdapter.Row> suggestions = suggestionRows();
            if (suggestions.isEmpty()) {
                rows.add(RowAdapter.Row.item("no-suggestions")
                        .avatar("–")
                        .title(getString(R.string.suggestions_none))
                        .subtitle(getString(R.string.suggestions_none_hint)));
            } else {
                rows.addAll(suggestions);
            }
        }
        adapter.submitList(rows);
        binding.emptyState.setVisibility(View.GONE);
    }

    private List<RowAdapter.Row> suggestionRows() {
        final List<String> needed = team.getSkillsNeeded();
        final java.util.Set<String> have = team.coveredSkills();
        List<Scored> scored = new ArrayList<>();
        for (Seeker s : seekers) {
            if (team.hasMember(s.getUid())) {
                continue;
            }
            TeamMatcher.Match m = TeamMatcher.score(s.getSkills(), needed, have);
            if (m.score > 0) {
                scored.add(new Scored(s, m));
            }
        }
        Collections.sort(scored, (a, b) -> {
            if (a.match.score != b.match.score) {
                return Integer.compare(b.match.score, a.match.score);
            }
            return lower(a.seeker.getName()).compareTo(lower(b.seeker.getName()));
        });

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Scored sc : scored.subList(0, Math.min(MAX_SUGGESTIONS, scored.size()))) {
            rows.add(RowAdapter.Row.item("s:" + sc.seeker.getUid())
                    .avatar(RowAdapter.Row.initial(sc.seeker.getName()))
                    .title(sc.seeker.getName())
                    .subtitle(MatchText.explain(this, sc.match))
                    .pill(getString(R.string.match_points, sc.match.score), RowAdapter.PILL_GREEN)
                    .primary(getString(R.string.action_invite))
                    .tag(sc.seeker));
        }
        return rows;
    }

    private void renderActions() {
        boolean member = isMember();
        boolean leader = isLeader();
        boolean canJoin = !member && myTeamId == null && team.isOpen() && !team.full();

        binding.actions.setVisibility(member || canJoin ? View.VISIBLE : View.GONE);
        binding.btnA.setVisibility(member || canJoin ? View.VISIBLE : View.GONE);
        binding.btnA.setText(member ? R.string.action_invite_code : R.string.action_join_team);
        binding.btnA.setOnClickListener(v -> {
            if (isMember()) {
                showInviteDialog();
            } else {
                TeamRepository.joinOpenTeam(teamId, profile, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Snackbar.make(binding.getRoot(), R.string.msg_team_joined,
                                Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
            }
        });

        binding.btnB.setVisibility(member ? View.VISIBLE : View.GONE);
        binding.btnB.setText(leader ? R.string.action_manage_team : R.string.action_leave_team);
        binding.btnB.setOnClickListener(v -> {
            if (isLeader()) {
                showManageDialog();
            } else {
                confirmLeave();
            }
        });
    }

    // ------------------------------------------------------------------ invite code, QR, contacts

    private void showInviteDialog() {
        TeamRepository.loadInviteCode(teamId, new Callback<String>() {
            @Override
            public void onSuccess(String code) {
                if (isDestroyed() || team == null) {
                    return;
                }
                final DialogTeamInviteBinding view = DialogTeamInviteBinding.inflate(getLayoutInflater());
                Bitmap qr = QrCodeGenerator.generate(QrPayload.forTeam(teamId, code), 600);
                view.ivTeamQr.setImageBitmap(qr);
                view.tvInviteCode.setText(code);
                pendingInviteMessage = getString(R.string.team_invite_message, team.getName(),
                        team.getEventTitle(), code);
                view.btnShareCode.setOnClickListener(v -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, pendingInviteMessage);
                    startActivity(Intent.createChooser(send, getString(R.string.action_share_code)));
                });
                view.btnPickContact.setOnClickListener(v -> {
                    Intent pick = new Intent(Intent.ACTION_PICK,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                    try {
                        contactLauncher.launch(pick);
                    } catch (ActivityNotFoundException e) {
                        showError(getString(R.string.msg_no_contacts_app));
                    }
                });
                inviteDialog = new MaterialAlertDialogBuilder(TeamDetailActivity.this)
                        .setTitle(R.string.dialog_invite_title)
                        .setView(view.getRoot())
                        .setPositiveButton(R.string.action_done, null)
                        .create();
                inviteDialog.show();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    /**
     * Reads the one phone number the person picked (the picker grants temporary access to just
     * that row, so READ_CONTACTS is not needed) and opens the SMS app with the invite text.
     */
    private void sendSms(Uri phoneRow) {
        String number = null;
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor c = getContentResolver().query(phoneRow, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                number = c.getString(0);
            }
        } catch (SecurityException e) {
            number = null;
        }
        if (number == null || number.trim().isEmpty()) {
            showError(getString(R.string.msg_contact_no_number));
            return;
        }
        Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)));
        sms.putExtra("sms_body", pendingInviteMessage == null ? "" : pendingInviteMessage);
        try {
            startActivity(sms);
        } catch (ActivityNotFoundException e) {
            showError(getString(R.string.msg_no_sms_app));
        }
    }

    // ------------------------------------------------------------------ leader and member actions

    private void showManageDialog() {
        final String[] items = {getString(R.string.action_edit_team), getString(R.string.action_disband_team)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_manage_team)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        TeamFormDialog.show(this, getLayoutInflater(), team,
                                (name, skills, note, open) -> TeamRepository.updateTeam(teamId, name,
                                        skills, note, open, quiet()));
                    } else {
                        confirmDisband();
                    }
                })
                .show();
    }

    private void confirmDisband() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_disband_title)
                .setMessage(R.string.dialog_disband_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_disband_team, (d, w) -> {
                    if (team != null) {
                        TeamRepository.disband(team, quiet());
                    }
                })
                .show();
    }

    private void confirmLeave() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_leave_team_title)
                .setMessage(R.string.dialog_leave_team_message)
                .setNegativeButton(R.string.action_keep, null)
                .setPositiveButton(R.string.action_leave_team, (d, w) -> {
                    if (team != null) {
                        TeamRepository.leave(team, profile.getUid(), new Callback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                showError(message);
                            }
                        });
                    }
                })
                .show();
    }

    @Override
    public void onRowClick(RowAdapter.Row row) {
    }

    @Override
    public void onPrimary(RowAdapter.Row row) {
        if (team == null) {
            return;
        }
        if (row.tag instanceof Seeker) {
            final Seeker s = (Seeker) row.tag;
            TeamRepository.invite(team, s.getUid(), new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Snackbar.make(binding.getRoot(), getString(R.string.msg_invite_sent, s.getName()),
                            Snackbar.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        } else if (row.tag instanceof String) {
            final String memberUid = (String) row.tag;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_remove_member_title)
                    .setMessage(getString(R.string.dialog_remove_member_message, team.nameOf(memberUid)))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_remove, (d, w) ->
                            TeamRepository.removeMember(team, memberUid, quiet()))
                    .show();
        }
    }

    @Override
    public void onSecondary(RowAdapter.Row row) {
    }

    private Callback<Void> quiet() {
        return new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // The live team updates by itself.
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        };
    }

    private void showError(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    private static String lower(@Nullable String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static final class Scored {
        final Seeker seeker;
        final TeamMatcher.Match match;

        Scored(Seeker seeker, TeamMatcher.Match match) {
            this.seeker = seeker;
            this.match = match;
        }
    }
}

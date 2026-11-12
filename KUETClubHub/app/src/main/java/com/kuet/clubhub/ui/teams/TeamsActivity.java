package com.kuet.clubhub.ui.teams;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

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
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.TeamRepository;
import com.kuet.clubhub.databinding.ActivityListBinding;
import com.kuet.clubhub.databinding.DialogJoinCodeBinding;
import com.kuet.clubhub.databinding.DialogTextInputBinding;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Seeker;
import com.kuet.clubhub.model.Team;
import com.kuet.clubhub.model.TeamInvite;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.notify.ToastCenter;
import com.kuet.clubhub.ui.common.RowAdapter;
import com.kuet.clubhub.ui.scan.ScanActivity;
import com.kuet.clubhub.util.Interests;
import com.kuet.clubhub.util.TeamMatcher;
import com.kuet.clubhub.util.Years;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The team hub of one competition: open teams, the looking-for-teammates board and my
 * invitations. Teams are ranked by how well my skills fit what they are asking for.
 */
public class TeamsActivity extends BaseActivity implements RowAdapter.Listener {

    public static final String EXTRA_EVENT_ID = "event_id";

    private static final int TAB_TEAMS = 0;
    private static final int TAB_PEOPLE = 1;
    private static final int TAB_INVITES = 2;

    private ActivityListBinding binding;
    private RowAdapter adapter;
    private UserProfile profile;
    private String eventId;

    private Event event;
    private MyRegistration myReg;
    private String myTeamId;
    private List<Team> teams = new ArrayList<>();
    private List<Seeker> seekers = new ArrayList<>();
    private List<TeamInvite> invites = new ArrayList<>();
    private boolean teamsLoaded = false;
    private int tab = TAB_TEAMS;

    private ListenerRegistration eventReg;
    private ListenerRegistration myRegReg;
    private ListenerRegistration myTeamReg;
    private ListenerRegistration teamsReg;
    private ListenerRegistration seekersReg;
    private ListenerRegistration invitesReg;

    private AlertDialog joinDialog;

    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    ToastCenter.show(this, R.string.msg_team_joined);
                    Snackbar.make(binding.getRoot(), R.string.msg_team_joined, Snackbar.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        profile = new SessionManager(this).getProfile();
        if (eventId == null || profile == null) {
            finish();
            return;
        }
        binding.toolbar.setTitle(R.string.title_teams);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new RowAdapter(this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.toggle.setVisibility(View.VISIBLE);
        binding.tab3.setVisibility(View.VISIBLE);
        binding.toggle.check(R.id.tab_1);
        binding.toggle.addOnButtonCheckedListener((group, id, isChecked) -> {
            if (isChecked) {
                tab = id == R.id.tab_1 ? TAB_TEAMS : id == R.id.tab_2 ? TAB_PEOPLE : TAB_INVITES;
                render();
            }
        });
        binding.btnA.setOnClickListener(v -> onActionA());
        binding.btnB.setOnClickListener(v -> showJoinCodeDialog());
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventId == null || profile == null) {
            return;
        }
        eventReg = EventRepository.getInstance(this).listenEvent(eventId,
                new EventRepository.EventListener() {
                    @Override
                    public void onEvent(Event e) {
                        event = e;
                        render();
                    }

                    @Override
                    public void onMissing() {
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
        myRegReg = EventRepository.getInstance(this).listenMyRegistration(profile.getUid(), eventId,
                new EventRepository.MyRegistrationListener() {
                    @Override
                    public void onChanged(MyRegistration reg) {
                        myReg = reg;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
        myTeamReg = TeamRepository.listenMyTeam(eventId, profile.getUid(),
                new TeamRepository.MyTeamListener() {
                    @Override
                    public void onChanged(@Nullable String teamId) {
                        myTeamId = teamId;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
        teamsReg = TeamRepository.listenTeams(eventId, new TeamRepository.TeamsListener() {
            @Override
            public void onTeams(List<Team> list) {
                teams = list;
                teamsLoaded = true;
                render();
            }

            @Override
            public void onError(String message) {
                teamsLoaded = true;
                render();
                showError(message);
            }
        });
        seekersReg = TeamRepository.listenSeekers(eventId, new TeamRepository.SeekersListener() {
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
        invitesReg = TeamRepository.listenInvites(profile.getUid(), new TeamRepository.InvitesListener() {
            @Override
            public void onInvites(List<TeamInvite> list) {
                List<TeamInvite> mine = new ArrayList<>();
                for (TeamInvite i : list) {
                    if (eventId.equals(i.getEventId())) {
                        mine.add(i);
                    }
                }
                invites = mine;
                render();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        eventReg = stop(eventReg);
        myRegReg = stop(myRegReg);
        myTeamReg = stop(myTeamReg);
        teamsReg = stop(teamsReg);
        seekersReg = stop(seekersReg);
        invitesReg = stop(invitesReg);
        if (joinDialog != null) {
            joinDialog.dismiss();
            joinDialog = null;
        }
    }

    private static ListenerRegistration stop(@Nullable ListenerRegistration r) {
        if (r != null) {
            r.remove();
        }
        return null;
    }

    // ------------------------------------------------------------------ rendering

    private boolean registered() {
        return myReg != null && MyRegistration.REGISTERED.equals(myReg.getStatus());
    }

    @Nullable
    private Team myTeam() {
        if (myTeamId == null) {
            return null;
        }
        for (Team t : teams) {
            if (myTeamId.equals(t.getId())) {
                return t;
            }
        }
        return null;
    }

    private void render() {
        if (binding == null) {
            return;
        }
        binding.tvTitle.setText(event == null ? "" : event.getTitle());
        if (event != null) {
            binding.tvSubtitle.setText(getString(R.string.teams_subtitle,
                    teamSizeText(event), (int) event.getRegisteredCount()));
        }

        binding.tab1.setText(getString(R.string.tab_teams_count, teams.size()));
        binding.tab2.setText(getString(R.string.tab_people_count, seekers.size()));
        binding.tab3.setText(getString(R.string.tab_invites_count, invites.size()));

        renderActions();

        List<RowAdapter.Row> rows;
        int emptyRes;
        if (tab == TAB_TEAMS) {
            rows = teamRows();
            emptyRes = R.string.empty_teams;
        } else if (tab == TAB_PEOPLE) {
            rows = peopleRows();
            emptyRes = R.string.empty_people;
        } else {
            rows = inviteRows();
            emptyRes = R.string.empty_invites;
        }
        adapter.submitList(rows);

        boolean waiting = tab == TAB_TEAMS && !teamsLoaded && rows.isEmpty();
        binding.progress.setVisibility(waiting ? View.VISIBLE : View.GONE);
        binding.emptyState.setVisibility(!waiting && rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.emptyState.setText(registered() || myReg == null ? emptyRes
                : R.string.teams_need_registration);
    }

    private void renderActions() {
        boolean canAct = registered() && event != null && event.getTeamSizeMax() > 0
                && !event.hasEnded(System.currentTimeMillis());
        boolean inTeam = myTeamId != null;
        binding.actions.setVisibility(canAct ? View.VISIBLE : View.GONE);
        binding.btnA.setVisibility(canAct ? View.VISIBLE : View.GONE);
        binding.btnA.setText(inTeam ? R.string.action_my_team : R.string.action_create_team);
        binding.btnB.setVisibility(canAct && !inTeam ? View.VISIBLE : View.GONE);
        binding.btnB.setText(R.string.action_join_with_code);
    }

    private String teamSizeText(Event e) {
        if (e.getTeamSizeMax() <= 0) {
            return "-";
        }
        return e.getTeamSizeMin() == e.getTeamSizeMax() ? String.valueOf(e.getTeamSizeMax())
                : e.getTeamSizeMin() + "–" + e.getTeamSizeMax();
    }

    private List<RowAdapter.Row> teamRows() {
        final boolean free = myTeamId == null;
        // Rank by how well my skills fit what each team wants, my own team first.
        final List<String> mySkills = profile.getSkills();
        List<Ranked<Team>> ranked = new ArrayList<>();
        for (Team t : teams) {
            TeamMatcher.Match m = TeamMatcher.score(mySkills, t.getSkillsNeeded(), t.coveredSkills());
            ranked.add(new Ranked<>(t, m, t.getId().equals(myTeamId)));
        }
        Collections.sort(ranked, (a, b) -> {
            if (a.mine != b.mine) {
                return a.mine ? -1 : 1;
            }
            if (a.match.score != b.match.score) {
                return Integer.compare(b.match.score, a.match.score);
            }
            return lower(a.item.getName()).compareTo(lower(b.item.getName()));
        });

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Ranked<Team> r : ranked) {
            Team t = r.item;
            StringBuilder sub = new StringBuilder(getString(R.string.team_row_summary,
                    t.size(), t.getMaxSize(), t.getLeaderName()));
            if (!t.getSkillsNeeded().isEmpty()) {
                sub.append('\n').append(getString(R.string.team_row_needs,
                        Interests.join(t.getSkillsNeeded())));
            }
            if (free && r.match.score > 0 && !r.mine) {
                sub.append('\n').append(getString(R.string.team_row_fit, MatchText.explain(this, r.match)));
            }
            RowAdapter.Row row = RowAdapter.Row.item(t.getId())
                    .avatar(RowAdapter.Row.initial(t.getName()))
                    .title(t.getName())
                    .subtitle(sub.toString())
                    .clickable(true)
                    .tag(t);
            if (r.mine) {
                row.pill(getString(R.string.pill_your_team), RowAdapter.PILL_GREEN);
            } else if (t.full()) {
                row.pill(getString(R.string.pill_full), RowAdapter.PILL_NEUTRAL);
            } else if (t.isOpen()) {
                row.pill(getString(R.string.pill_open), RowAdapter.PILL_GREEN);
                if (free && registered()) {
                    row.primary(getString(R.string.action_join));
                }
            } else {
                row.pill(getString(R.string.pill_invite_only), RowAdapter.PILL_NEUTRAL);
            }
            rows.add(row);
        }
        return rows;
    }

    private List<RowAdapter.Row> peopleRows() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        final Team mine = myTeam();
        final boolean iLead = mine != null && profile.getUid().equals(mine.getLeaderUid());

        // The first row is my own post (only for a registered student who has no team).
        if (myTeamId == null && registered()) {
            boolean posted = false;
            for (Seeker s : seekers) {
                if (profile.getUid().equals(s.getUid())) {
                    posted = true;
                    break;
                }
            }
            RowAdapter.Row own = RowAdapter.Row.item("self")
                    .avatar("＋")
                    .title(getString(posted ? R.string.seeker_posted_title : R.string.seeker_post_title))
                    .subtitle(getString(posted ? R.string.seeker_posted_text : R.string.seeker_post_text))
                    .primary(getString(posted ? R.string.action_remove_post : R.string.action_post_request));
            own.tag(Boolean.valueOf(posted));
            rows.add(own);
        }

        List<Ranked<Seeker>> ranked = new ArrayList<>();
        for (Seeker s : seekers) {
            if (profile.getUid().equals(s.getUid())) {
                continue;
            }
            TeamMatcher.Match m = mine == null ? TeamMatcher.score(s.getSkills(), null, null)
                    : TeamMatcher.score(s.getSkills(), mine.getSkillsNeeded(), mine.coveredSkills());
            ranked.add(new Ranked<>(s, m, false));
        }
        Collections.sort(ranked, (a, b) -> {
            if (a.match.score != b.match.score) {
                return Integer.compare(b.match.score, a.match.score);
            }
            return lower(a.item.getName()).compareTo(lower(b.item.getName()));
        });

        for (Ranked<Seeker> r : ranked) {
            Seeker s = r.item;
            StringBuilder sub = new StringBuilder(s.getDepartment() + "  •  " + Years.label(s.getYear()));
            if (s.getSkills() != null && !s.getSkills().isEmpty()) {
                sub.append('\n').append(getString(R.string.seeker_skills, Interests.join(s.getSkills())));
            }
            if (s.getNote() != null && !s.getNote().trim().isEmpty()) {
                sub.append("\n“").append(s.getNote().trim()).append('”');
            }
            if (iLead) {
                String why = MatchText.explain(this, r.match);
                if (!why.isEmpty()) {
                    sub.append('\n').append(why);
                }
            }
            RowAdapter.Row row = RowAdapter.Row.item(s.getUid())
                    .avatar(RowAdapter.Row.initial(s.getName()))
                    .uid(s.getUid())
                    .title(s.getName())
                    .subtitle(sub.toString())
                    .tag(s);
            if (iLead && r.match.score > 0) {
                row.pill(getString(R.string.match_points, r.match.score), RowAdapter.PILL_GREEN);
            }
            if (iLead && !mine.full()) {
                row.primary(getString(R.string.action_invite));
            }
            rows.add(row);
        }
        return rows;
    }

    private List<RowAdapter.Row> inviteRows() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (TeamInvite i : invites) {
            RowAdapter.Row row = RowAdapter.Row.item(i.getTeamId())
                    .avatar(RowAdapter.Row.initial(i.getTeamName()))
                    .title(i.getTeamName())
                    .subtitle(getString(R.string.invite_from, i.getFromName()))
                    .tag(i);
            if (myTeamId == null) {
                row.primary(getString(R.string.action_accept));
            } else {
                row.pill(getString(R.string.pill_in_a_team), RowAdapter.PILL_NEUTRAL);
            }
            row.secondary(getString(R.string.action_decline));
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------------ actions

    private void onActionA() {
        if (myTeamId != null) {
            openTeam(myTeamId);
            return;
        }
        if (event == null) {
            return;
        }
        TeamFormDialog.show(this, getLayoutInflater(), null, (name, skills, note, open) ->
                TeamRepository.createTeam(event, profile, name, skills, note, open,
                        new Callback<String>() {
                            @Override
                            public void onSuccess(String teamId) {
                                ToastCenter.showShort(TeamsActivity.this,
                                        "Team \u201C" + name + "\u201D created!");
                                openTeam(teamId);
                            }

                            @Override
                            public void onError(String message) {
                                showError(message);
                            }
                        }));
    }

    private void showJoinCodeDialog() {
        final DialogJoinCodeBinding form = DialogJoinCodeBinding.inflate(getLayoutInflater());
        joinDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_join_code_title)
                .setView(form.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_join, (d, w) -> {
                    String code = form.etCode.getText() == null ? ""
                            : form.etCode.getText().toString();
                    TeamRepository.joinWithCode(code, profile, joined());
                })
                .create();
        form.btnScanTeamQr.setOnClickListener(v -> {
            joinDialog.dismiss();
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_JOIN_TEAM);
            scanLauncher.launch(intent);
        });
        joinDialog.show();
    }

    private Callback<Void> joined() {
        return new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                ToastCenter.show(TeamsActivity.this, R.string.msg_team_joined);
                Snackbar.make(binding.getRoot(), R.string.msg_team_joined, Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        };
    }

    @Override
    public void onRowClick(RowAdapter.Row row) {
        if (tab == TAB_TEAMS && row.tag instanceof Team) {
            openTeam(((Team) row.tag).getId());
        }
    }

    @Override
    public void onPrimary(RowAdapter.Row row) {
        if (tab == TAB_TEAMS && row.tag instanceof Team) {
            TeamRepository.joinOpenTeam(((Team) row.tag).getId(), profile, joined());
        } else if (tab == TAB_PEOPLE && row.tag instanceof Boolean) {
            boolean posted = (Boolean) row.tag;
            if (posted) {
                TeamRepository.removeSeeker(eventId, profile.getUid(), new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        ToastCenter.showShort(TeamsActivity.this, "Teammate request removed.");
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
            } else {
                askForPostNote();
            }
        } else if (tab == TAB_PEOPLE && row.tag instanceof Seeker) {
            Team mine = myTeam();
            if (mine != null) {
                final Seeker s = (Seeker) row.tag;
                TeamRepository.invite(mine, s.getUid(), new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        String msg = getString(R.string.msg_invite_sent, s.getName());
                        ToastCenter.showShort(TeamsActivity.this, msg);
                        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
            }
        } else if (tab == TAB_INVITES && row.tag instanceof TeamInvite) {
            TeamRepository.acceptInvite((TeamInvite) row.tag, profile, joined());
        }
    }

    @Override
    public void onSecondary(RowAdapter.Row row) {
        if (tab == TAB_INVITES && row.tag instanceof TeamInvite) {
            TeamInvite inv = (TeamInvite) row.tag;
            TeamRepository.declineInvite(profile.getUid(), inv.getTeamId(), new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    ToastCenter.showShort(TeamsActivity.this,
                            "Declined invitation from " + inv.getTeamName() + ".");
                }

                @Override
                public void onError(String message) {
                    showError(message);
                }
            });
        }
    }

    private void askForPostNote() {
        final DialogTextInputBinding input = DialogTextInputBinding.inflate(getLayoutInflater());
        input.tilText.setHint(getString(R.string.hint_post_note));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_post_title)
                .setMessage(R.string.dialog_post_message)
                .setView(input.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_post_request, (d, w) -> {
                    String note = input.etText.getText() == null ? ""
                            : input.etText.getText().toString().trim();
                    TeamRepository.postSeeker(eventId, profile, note, new Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            ToastCenter.showShort(TeamsActivity.this,
                                    "Your looking-for-team request is now live!");
                        }

                        @Override
                        public void onError(String message) {
                            showError(message);
                        }
                    });
                })
                .show();
    }

    private void openTeam(String teamId) {
        Intent intent = new Intent(this, TeamDetailActivity.class);
        intent.putExtra(TeamDetailActivity.EXTRA_TEAM_ID, teamId);
        startActivity(intent);
    }

    private Callback<Void> quiet() {
        return new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // The live lists update by themselves.
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        };
    }

    private void showError(String message) {
        ToastCenter.show(this, message);
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    private static String lower(@Nullable String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** A list item together with its match score. */
    private static final class Ranked<T> {
        final T item;
        final TeamMatcher.Match match;
        final boolean mine;

        Ranked(T item, TeamMatcher.Match match, boolean mine) {
            this.item = item;
            this.match = match;
            this.mine = mine;
        }
    }
}

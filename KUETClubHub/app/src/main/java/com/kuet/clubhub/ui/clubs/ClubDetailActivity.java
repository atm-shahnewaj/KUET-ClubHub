package com.kuet.clubhub.ui.clubs;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.ClubNoticeRepository;
import com.kuet.clubhub.data.ClubRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.MembershipRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.local.EventEntity;
import com.kuet.clubhub.databinding.ActivityClubDetailBinding;
import com.kuet.clubhub.databinding.DialogClubNoticeBinding;
import com.kuet.clubhub.databinding.DialogTextInputBinding;
import com.kuet.clubhub.databinding.ItemClubNoticeBinding;
import com.kuet.clubhub.model.Club;
import com.kuet.clubhub.model.ClubMember;
import com.kuet.clubhub.model.ClubNotice;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.common.RowAdapter;
import com.kuet.clubhub.ui.events.EventAdapter;
import com.kuet.clubhub.ui.events.EventDetailActivity;
import com.kuet.clubhub.ui.events.EventEditActivity;
import com.kuet.clubhub.util.ImageHelper;
import com.kuet.clubhub.util.TimeFormats;
import com.kuet.clubhub.util.Years;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-featured Club Hub screen:
 * - Hero Club Identity card with live stats (Members, Events, Upcoming),
 *   Join/Leave/Withdraw controls, Members-Only Group Chat launcher,
 *   and Organizer/Admin Quick Console
 * - Tab 1: Events (Upcoming & Past events hosted by this club)
 * - Tab 2: Chat (Members-only real-time group chat via Firebase Realtime Database)
 * - Tab 3: Notices (Pinned announcements & updates posted by club organizers)
 * - Tab 4: About (Club description, Focus Highlights & Active Members Directory preview)
 */
public class ClubDetailActivity extends BaseActivity {

    public static final String EXTRA_CLUB_ID = "club_id";

    private static final int TAB_EVENTS = 0;
    private static final int TAB_CHAT = 1;
    private static final int TAB_NOTICES = 2;
    private static final int TAB_ABOUT = 3;

    private static final int EVENT_FILTER_ALL = 0;
    private static final int EVENT_FILTER_UPCOMING = 1;
    private static final int EVENT_FILTER_PAST = 2;

    private ActivityClubDetailBinding binding;
    private ClubRepository repository;
    private EventRepository eventRepository;
    private UserProfile profile;
    private String clubId;

    private ListenerRegistration registration;
    private ListenerRegistration membershipRegistration;
    private ListenerRegistration eventSyncRegistration;
    private ListenerRegistration myRegsRegistration;
    private ListenerRegistration noticesRegistration;
    private ListenerRegistration approvedMembersRegistration;

    private Club currentClub;
    private ClubMember myMembership;
    private boolean membershipLoaded = false;
    private boolean busy = false;

    private int activeTab = TAB_EVENTS;
    private int eventFilter = EVENT_FILTER_ALL;

    private final List<Event> allCachedEvents = new ArrayList<>();
    private final List<ClubNotice> clubNotices = new ArrayList<>();
    private final List<ClubMember> approvedMembers = new ArrayList<>();

    private EventAdapter clubEventAdapter;
    private NoticeAdapter noticeAdapter;
    private RowAdapter memberPreviewAdapter;

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
        eventRepository = EventRepository.getInstance(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        View.OnClickListener openChatClick = v -> onGroupChatClicked();
        binding.btnClubChat.setOnClickListener(openChatClick);
        binding.btnOpenChatRoom.setOnClickListener(openChatClick);

        setupTabsAndAdapters();

        eventRepository.observeCachedEvents().observe(this, entities -> {
            allCachedEvents.clear();
            if (entities != null) {
                for (EventEntity e : entities) {
                    allCachedEvents.add(e.toEvent());
                }
            }
            renderClubEvents();
        });
    }

    private void setupTabsAndAdapters() {
        clubEventAdapter = new EventAdapter(true, event -> {
            Intent intent = new Intent(this, EventDetailActivity.class);
            intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.getId());
            startActivity(intent);
        });
        binding.recyclerClubEvents.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerClubEvents.setAdapter(clubEventAdapter);

        noticeAdapter = new NoticeAdapter();
        binding.recyclerClubNotices.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerClubNotices.setAdapter(noticeAdapter);

        memberPreviewAdapter = new RowAdapter(new RowAdapter.Listener() {
            @Override public void onRowClick(RowAdapter.Row row) {}
            @Override public void onPrimary(RowAdapter.Row row) {}
            @Override public void onSecondary(RowAdapter.Row row) {}
        });
        binding.recyclerClubMembersPreview.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerClubMembersPreview.setAdapter(memberPreviewAdapter);

        binding.toggleClubTabs.check(R.id.btn_tab_events);
        binding.toggleClubTabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_tab_events) {
                activeTab = TAB_EVENTS;
            } else if (checkedId == R.id.btn_tab_chat) {
                activeTab = TAB_CHAT;
            } else if (checkedId == R.id.btn_tab_notices) {
                activeTab = TAB_NOTICES;
            } else {
                activeTab = TAB_ABOUT;
            }
            updateTabVisibility();
        });

        binding.chipGroupEventFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_events_upcoming) {
                eventFilter = EVENT_FILTER_UPCOMING;
            } else if (id == R.id.chip_events_past) {
                eventFilter = EVENT_FILTER_PAST;
            } else {
                eventFilter = EVENT_FILTER_ALL;
            }
            renderClubEvents();
        });

        View.OnClickListener createEventClick = v -> openCreateEventForClub();
        binding.btnCreateClubEvent.setOnClickListener(createEventClick);
        binding.btnEmptyCreateEvent.setOnClickListener(createEventClick);

        View.OnClickListener postNoticeClick = v -> showPostNoticeDialog();
        binding.btnPostNotice.setOnClickListener(postNoticeClick);
        binding.btnAddNoticeInline.setOnClickListener(postNoticeClick);

        binding.btnExploreEvents.setOnClickListener(v -> {
            binding.toggleClubTabs.check(R.id.btn_tab_events);
            activeTab = TAB_EVENTS;
            updateTabVisibility();
        });

        updateTabVisibility();
    }

    private void updateTabVisibility() {
        if (binding == null) return;
        binding.sectionEvents.setVisibility(activeTab == TAB_EVENTS ? View.VISIBLE : View.GONE);
        binding.sectionChat.setVisibility(activeTab == TAB_CHAT ? View.VISIBLE : View.GONE);
        binding.sectionNotices.setVisibility(activeTab == TAB_NOTICES ? View.VISIBLE : View.GONE);
        binding.sectionAbout.setVisibility(activeTab == TAB_ABOUT ? View.VISIBLE : View.GONE);
        renderChatAccessState();
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

        eventSyncRegistration = eventRepository.startSync(new ClubRepository.SyncListener() {
            @Override public void onSynced() {}
            @Override public void onOfflineEmpty() {}
            @Override public void onError(String message) {}
        });

        if (profile.getRoleEnum() != Role.ADMIN) {
            membershipRegistration = MembershipRepository.listenMine(clubId, profile.getUid(),
                    new MembershipRepository.MyMembershipListener() {
                        @Override
                        public void onChanged(@Nullable ClubMember member) {
                            myMembership = member;
                            membershipLoaded = true;
                            renderMembership();
                            renderChatAccessState();
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });

            myRegsRegistration = eventRepository.listenMyRegistrations(profile.getUid(),
                    new EventRepository.MyRegistrationsListener() {
                        @Override
                        public void onChanged(List<MyRegistration> registrations) {
                            Map<String, String> status = new HashMap<>();
                            for (MyRegistration r : registrations) {
                                if (r.isActive()) {
                                    status.put(r.getEventId(), r.getStatus());
                                }
                            }
                            if (clubEventAdapter != null) {
                                clubEventAdapter.setMyStatus(status);
                            }
                        }

                        @Override
                        public void onError(String message) {
                        }
                    });
        } else {
            membershipLoaded = true;
        }

        noticesRegistration = ClubNoticeRepository.listen(this, clubId, currentClub, notices -> {
            clubNotices.clear();
            clubNotices.addAll(notices);
            renderNotices();
        });

        approvedMembersRegistration = MembershipRepository.listenApproved(clubId,
                new MembershipRepository.MembersListener() {
                    @Override
                    public void onMembers(List<ClubMember> members) {
                        approvedMembers.clear();
                        approvedMembers.addAll(members);
                        renderMembersPreview();
                    }

                    @Override
                    public void onError(String message) {
                        renderMembersPreview();
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) { registration.remove(); registration = null; }
        if (membershipRegistration != null) { membershipRegistration.remove(); membershipRegistration = null; }
        if (eventSyncRegistration != null) { eventSyncRegistration.remove(); eventSyncRegistration = null; }
        if (myRegsRegistration != null) { myRegsRegistration.remove(); myRegsRegistration = null; }
        if (noticesRegistration != null) { noticesRegistration.remove(); noticesRegistration = null; }
        if (approvedMembersRegistration != null) { approvedMembersRegistration.remove(); approvedMembersRegistration = null; }
    }

    private boolean isClubManager() {
        if (profile == null || currentClub == null) return false;
        Role role = profile.getRoleEnum();
        return role == Role.ADMIN || (role == Role.ORGANIZER && currentClub.hasOrganizer(profile.getUid()));
    }

    private boolean canUseGroupChat() {
        if (isClubManager()) return true;
        return myMembership != null && ClubMember.APPROVED.equals(myMembership.getStatus());
    }

    private void renderChatAccessState() {
        if (binding == null) return;
        boolean allowed = canUseGroupChat();
        if (allowed) {
            binding.btnClubChat.setText("Open Club Group Chat");
            binding.tvChatCardTitle.setText("Club Group Chat (Unlocked)");
            binding.tvChatCardDesc.setText("You are an approved member of this club. Chat live with fellow club members and organizers via Firebase Realtime Database.");
            binding.btnOpenChatRoom.setText("Enter Club Chat Room");
        } else {
            binding.btnClubChat.setText("Club Group Chat (Members Only)");
            binding.tvChatCardTitle.setText("Members-Only Club Group Chat");
            if (myMembership != null && ClubMember.PENDING.equals(myMembership.getStatus())) {
                binding.tvChatCardDesc.setText("Your membership request is waiting for organizer approval. Group chat unlocks as soon as you're approved!");
                binding.btnOpenChatRoom.setText("Application Pending");
            } else {
                binding.tvChatCardDesc.setText("Only approved members of this club can chat in this group room. Apply to join the club first!");
                binding.btnOpenChatRoom.setText("Apply to Join Club");
            }
        }
    }

    private void onGroupChatClicked() {
        if (canUseGroupChat()) {
            Intent chat = new Intent(this, ClubChatActivity.class);
            chat.putExtra(ClubChatActivity.EXTRA_CLUB_ID, clubId);
            if (currentClub != null && currentClub.getName() != null) {
                chat.putExtra(ClubChatActivity.EXTRA_CLUB_NAME, currentClub.getName());
            }
            startActivity(chat);
        } else if (myMembership != null && ClubMember.PENDING.equals(myMembership.getStatus())) {
            Snackbar.make(binding.getRoot(),
                    "Your club membership application is pending approval. Group chat is for approved members only.",
                    Snackbar.LENGTH_LONG).show();
        } else if (currentClub != null && currentClub.isApproved()) {
            askForMessageAndApply();
        } else {
            Snackbar.make(binding.getRoot(),
                    "Only approved club members can access the group chat.",
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void bind(Club club) {
        currentClub = club;
        String name = club.getName() == null ? "" : club.getName();
        binding.toolbar.setTitle(name.isEmpty() ? getString(R.string.title_club) : name);
        binding.tvAvatar.setText(name.isEmpty() ? "?"
                : name.substring(0, 1).toUpperCase(Locale.ROOT));
        ImageHelper.bindClubLogo(binding.ivClubLogo, club.getLogoBase64());
        binding.tvName.setText(name);
        binding.tvCategory.setText(club.getCategory() == null ? "Campus" : club.getCategory());
        int members = (int) club.getMemberCount();
        binding.tvMembers.setText(getResources()
                .getQuantityString(R.plurals.member_count, members, members));
        binding.tvStatMembers.setText(String.valueOf(members));
        binding.tvDescription.setText(club.getDescription());
        binding.tvPending.setVisibility(club.isApproved() ? View.GONE : View.VISIBLE);

        populateHighlights(club.getCategory());

        Role role = profile.getRoleEnum();
        boolean isAdmin = role == Role.ADMIN;
        boolean manager = isClubManager();

        binding.btnApprove.setVisibility(isAdmin && !club.isApproved() ? View.VISIBLE : View.GONE);
        binding.btnApprove.setOnClickListener(v -> approve());

        binding.layoutOrganizerActions.setVisibility(manager ? View.VISIBLE : View.GONE);
        binding.btnAddNoticeInline.setVisibility(manager ? View.VISIBLE : View.GONE);
        binding.btnOpenMembersDirectory.setVisibility(manager && club.isApproved() ? View.VISIBLE : View.GONE);

        View.OnClickListener openMembersScreen = v -> {
            Intent intent = new Intent(this, ClubMembersActivity.class);
            intent.putExtra(ClubMembersActivity.EXTRA_CLUB_ID, club.getId());
            intent.putExtra(ClubMembersActivity.EXTRA_CLUB_NAME, club.getName());
            startActivity(intent);
        };
        binding.btnMembers.setVisibility(manager && club.isApproved() ? View.VISIBLE : View.GONE);
        binding.btnMembers.setOnClickListener(openMembersScreen);
        binding.btnOpenMembersDirectory.setOnClickListener(openMembersScreen);

        binding.btnEdit.setVisibility(manager ? View.VISIBLE : View.GONE);
        binding.btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, ClubEditActivity.class);
            intent.putExtra(ClubEditActivity.EXTRA_CLUB_ID, club.getId());
            intent.putExtra(ClubEditActivity.EXTRA_NAME, club.getName());
            intent.putExtra(ClubEditActivity.EXTRA_CATEGORY, club.getCategory());
            intent.putExtra(ClubEditActivity.EXTRA_DESCRIPTION, club.getDescription());
            intent.putExtra(ClubEditActivity.EXTRA_LOGO, club.getLogoBase64());
            startActivity(intent);
        });

        renderMembership();
        renderChatAccessState();
        renderClubEvents();
        renderNotices();
        renderMembersPreview();
    }

    private void populateHighlights(@Nullable String category) {
        binding.chipGroupHighlights.removeAllViews();
        String cat = category == null ? "" : category.toLowerCase(Locale.ROOT);
        String[] tags;
        if (cat.contains("tech") || cat.contains("cyber") || cat.contains("robot")) {
            tags = new String[]{"Workshops", "Hackathons", "Project Showcases", "Coding Sprints", "Peer Mentorship"};
        } else if (cat.contains("debate") || cat.contains("career")) {
            tags = new String[]{"Parliamentary Debate", "Public Speaking", "Seminars", "Inter-University Tournaments"};
        } else if (cat.contains("cultur") || cat.contains("art") || cat.contains("photo")) {
            tags = new String[]{"Campus Festivals", "Creative Exhibitions", "Stage Performances", "Skill Workshops"};
        } else if (cat.contains("sport")) {
            tags = new String[]{"Campus Leagues", "Fitness Sessions", "Inter-Dept Tournaments", "Team Trials"};
        } else {
            tags = new String[]{"Campus Events", "Skill Building", "Seminars", "Competitions", "Student Networking"};
        }
        for (String tag : tags) {
            Chip chip = (Chip) getLayoutInflater().inflate(
                    R.layout.item_filter_chip, binding.chipGroupHighlights, false);
            chip.setText(tag);
            chip.setCheckable(false);
            chip.setClickable(false);
            binding.chipGroupHighlights.addView(chip);
        }
    }

    // ------------------------------------------------------------------ club events tab

    private void renderClubEvents() {
        if (binding == null) return;
        List<Event> clubEvents = new ArrayList<>();
        String cName = currentClub != null && currentClub.getName() != null
                ? currentClub.getName().trim().toLowerCase(Locale.ROOT) : "";

        long now = System.currentTimeMillis();
        int upcomingCount = 0;

        for (Event e : allCachedEvents) {
            boolean matchId = clubId != null && clubId.equals(e.getClubId());
            boolean matchName = !cName.isEmpty() && e.getClubName() != null
                    && cName.equals(e.getClubName().trim().toLowerCase(Locale.ROOT));
            if (matchId || matchName) {
                clubEvents.add(e);
                if (!e.hasEnded(now)) {
                    upcomingCount++;
                }
            }
        }

        binding.tvStatEvents.setText(String.valueOf(clubEvents.size()));
        binding.tvStatUpcoming.setText(String.valueOf(upcomingCount));

        List<Event> filtered = new ArrayList<>();
        for (Event e : clubEvents) {
            boolean ended = e.hasEnded(now);
            if (eventFilter == EVENT_FILTER_UPCOMING && ended) continue;
            if (eventFilter == EVENT_FILTER_PAST && !ended) continue;
            filtered.add(e);
        }

        Collections.sort(filtered, (a, b) -> {
            boolean aEnded = a.hasEnded(now);
            boolean bEnded = b.hasEnded(now);
            if (aEnded != bEnded) return aEnded ? 1 : -1;
            long ta = a.getStartTime() == null ? 0 : a.getStartTime().getTime();
            long tb = b.getStartTime() == null ? 0 : b.getStartTime().getTime();
            return aEnded ? Long.compare(tb, ta) : Long.compare(ta, tb);
        });

        clubEventAdapter.submitList(filtered);
        boolean empty = filtered.isEmpty();
        binding.emptyClubEvents.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerClubEvents.setVisibility(empty ? View.GONE : View.VISIBLE);

        boolean canPostEvent = isClubManager() && (currentClub == null || currentClub.isApproved());
        binding.btnEmptyCreateEvent.setVisibility(empty && canPostEvent ? View.VISIBLE : View.GONE);
        if (empty) {
            if (eventFilter == EVENT_FILTER_UPCOMING) {
                binding.tvEmptyClubEvents.setText("No upcoming events scheduled right now.");
            } else if (eventFilter == EVENT_FILTER_PAST) {
                binding.tvEmptyClubEvents.setText("No past events recorded for this club yet.");
            } else {
                binding.tvEmptyClubEvents.setText("This club hasn't posted any events yet.");
            }
        }
    }

    private void openCreateEventForClub() {
        if (currentClub != null && !currentClub.isApproved()) {
            Snackbar.make(binding.getRoot(), R.string.msg_no_clubs_for_events, Snackbar.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, EventEditActivity.class);
        intent.putExtra(EventEditActivity.EXTRA_PRESELECT_CLUB_ID, clubId);
        if (currentClub != null && currentClub.getName() != null) {
            intent.putExtra(EventEditActivity.EXTRA_PRESELECT_CLUB_NAME, currentClub.getName());
        }
        startActivity(intent);
    }

    // ------------------------------------------------------------------ club notices tab

    private void renderNotices() {
        if (binding == null) return;
        List<ClubNotice> shown = new ArrayList<>(clubNotices);
        if (shown.isEmpty() && currentClub != null) {
            ClubNoticeRepository.listen(this, clubId, currentClub, list -> {
                clubNotices.clear();
                clubNotices.addAll(list);
                if (noticeAdapter != null) {
                    noticeAdapter.submit(clubNotices);
                }
            });
            return;
        }
        noticeAdapter.submit(shown);
    }

    private void showPostNoticeDialog() {
        final DialogClubNoticeBinding form = DialogClubNoticeBinding.inflate(getLayoutInflater());
        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Post Club Announcement")
                .setView(form.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_post, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = form.etNoticeTitle.getText() == null ? ""
                    : form.etNoticeTitle.getText().toString().trim();
            String body = form.etNoticeBody.getText() == null ? ""
                    : form.etNoticeBody.getText().toString().trim();
            form.tilNoticeTitle.setError(null);
            form.tilNoticeBody.setError(null);
            if (title.length() < 3) {
                form.tilNoticeTitle.setError("Enter a title (at least 3 characters)");
                return;
            }
            if (body.length() < 5) {
                form.tilNoticeBody.setError("Enter announcement details");
                return;
            }
            boolean pinned = form.switchPinned.isChecked();
            dialog.dismiss();
            ClubNoticeRepository.post(this, clubId, profile, title, body, pinned, new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    if (isDestroyed() || binding == null) return;
                    binding.toggleClubTabs.check(R.id.btn_tab_notices);
                    activeTab = TAB_NOTICES;
                    updateTabVisibility();
                    ClubNoticeRepository.listen(ClubDetailActivity.this, clubId, currentClub, list -> {
                        clubNotices.clear();
                        clubNotices.addAll(list);
                        renderNotices();
                    });
                    Snackbar.make(binding.getRoot(), "Announcement published to club bulletin.",
                            Snackbar.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    if (!isDestroyed() && binding != null) {
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                    }
                }
            });
        }));
        dialog.show();
    }

    private class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.Holder> {
        private List<ClubNotice> items = new ArrayList<>();

        void submit(List<ClubNotice> list) {
            this.items = new ArrayList<>(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemClubNoticeBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ClubNotice n = items.get(position);
            holder.binding.tvTitle.setText(n.getTitle());
            holder.binding.tvBody.setText(n.getBody());
            holder.binding.tvBadge.setText(n.isPinned() ? "Pinned Notice" : "Club Update");
            holder.binding.tvBadge.setBackgroundResource(
                    n.isPinned() ? R.drawable.bg_pill : R.drawable.bg_pill_neutral);
            holder.binding.tvDate.setText(n.getCreatedAt() == null ? ""
                    : TimeFormats.fullDate(n.getCreatedAt()));
            String author = n.getAuthorName() == null || n.getAuthorName().isEmpty()
                    ? "Club Organizer" : n.getAuthorName();
            holder.binding.tvAuthor.setText("Posted by " + author);

            boolean canDelete = isClubManager()
                    && n.getId() != null && !n.getId().startsWith("default_");
            holder.binding.btnDeleteNotice.setVisibility(canDelete ? View.VISIBLE : View.GONE);
            holder.binding.btnDeleteNotice.setOnClickListener(v ->
                    ClubNoticeRepository.delete(ClubDetailActivity.this, clubId, n.getId(),
                            new Callback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    ClubNoticeRepository.listen(ClubDetailActivity.this, clubId, currentClub, list -> {
                                        clubNotices.clear();
                                        clubNotices.addAll(list);
                                        renderNotices();
                                    });
                                }

                                @Override
                                public void onError(String message) {
                                }
                            }));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemClubNoticeBinding binding;
            Holder(ItemClubNoticeBinding b) {
                super(b.getRoot());
                this.binding = b;
            }
        }
    }

    // ------------------------------------------------------------------ about & members preview

    private void renderMembersPreview() {
        if (binding == null) return;
        List<ClubMember> sorted = new ArrayList<>(approvedMembers);
        Collections.sort(sorted, (a, b) -> {
            String na = a.getName() == null ? "" : a.getName().toLowerCase(Locale.ROOT);
            String nb = b.getName() == null ? "" : b.getName().toLowerCase(Locale.ROOT);
            return na.compareTo(nb);
        });

        int count = currentClub != null
                ? Math.max((int) currentClub.getMemberCount(), sorted.size())
                : sorted.size();
        binding.tvDirectoryCount.setText(count + " active");

        List<RowAdapter.Row> rows = new ArrayList<>();
        int limit = Math.min(8, sorted.size());
        for (int i = 0; i < limit; i++) {
            ClubMember m = sorted.get(i);
            String dept = m.getDepartment() == null ? "KUET" : m.getDepartment();
            String sub = dept + (m.getYear() > 0 ? "  •  " + Years.label(m.getYear()) : "");
            rows.add(RowAdapter.Row.item(m.getUid())
                    .avatar(RowAdapter.Row.initial(m.getName()))
                    .uid(m.getUid())
                    .title(m.getName())
                    .subtitle(sub)
                    .pill("Member", RowAdapter.PILL_GREEN));
        }
        memberPreviewAdapter.submitList(rows);
        binding.tvEmptyMembersPreview.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ membership

    /** Join / leave controls for students (and organizers of other clubs). */
    private void renderMembership() {
        binding.tvMembership.setVisibility(View.GONE);
        binding.btnJoin.setVisibility(View.GONE);
        binding.btnLeaveClub.setVisibility(View.GONE);

        Role role = profile.getRoleEnum();
        boolean owner = currentClub != null && currentClub.hasOrganizer(profile.getUid());
        if (owner) {
            binding.tvMembership.setVisibility(View.VISIBLE);
            binding.tvMembership.setText("You organize this club");
            return;
        }
        if (currentClub == null || !membershipLoaded || role == Role.ADMIN
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

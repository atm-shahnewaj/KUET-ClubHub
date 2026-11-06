package com.kuet.clubhub.ui.clubs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.ClubChatRepository;
import com.kuet.clubhub.data.ClubRepository;
import com.kuet.clubhub.data.MembershipRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityClubChatBinding;
import com.kuet.clubhub.databinding.ItemChatMessageBinding;
import com.kuet.clubhub.model.ChatMessage;
import com.kuet.clubhub.model.Club;
import com.kuet.clubhub.model.ClubMember;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.TimeFormats;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Members-Only Club Group Chat powered by Firebase Realtime Database (`club_chats/{clubId}/messages`).
 * Only approved club members, organizers of this club, and admins can view and post messages.
 */
public class ClubChatActivity extends BaseActivity {

    public static final String EXTRA_CLUB_ID = "club_id";
    public static final String EXTRA_CLUB_NAME = "club_name";

    private ActivityClubChatBinding binding;
    private UserProfile profile;
    private String clubId;
    private String clubName;

    private Club currentClub;
    private ClubMember myMembership;
    private boolean clubLoaded = false;
    private boolean membershipLoaded = false;

    private ListenerRegistration clubReg;
    private ListenerRegistration memberReg;
    private ClubChatRepository.Handle chatHandle;

    private ChatAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClubChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        clubId = getIntent().getStringExtra(EXTRA_CLUB_ID);
        clubName = getIntent().getStringExtra(EXTRA_CLUB_NAME);
        profile = new SessionManager(this).getProfile();
        if (clubId == null || profile == null) {
            finish();
            return;
        }

        binding.toolbar.setTitle(clubName == null || clubName.isEmpty()
                ? "Club Group Chat" : clubName + " Chat");
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ChatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        binding.recyclerMessages.setLayoutManager(lm);
        binding.recyclerMessages.setAdapter(adapter);

        binding.btnSend.setOnClickListener(v -> sendMessage());
        binding.etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (clubId == null || profile == null) {
            return;
        }
        clubReg = ClubRepository.getInstance(this).listenClub(clubId, new ClubRepository.ClubListener() {
            @Override
            public void onClub(Club club) {
                currentClub = club;
                clubLoaded = true;
                if (club.getName() != null && !club.getName().isEmpty()) {
                    clubName = club.getName();
                    binding.toolbar.setTitle(clubName + " Chat");
                }
                binding.tvMemberBadge.setText(club.getMemberCount() + " Members");
                evaluateAccessAndRender();
            }

            @Override
            public void onMissing() {
                finish();
            }

            @Override
            public void onError(String message) {
                clubLoaded = true;
                evaluateAccessAndRender();
            }
        });

        if (profile.getRoleEnum() == Role.ADMIN) {
            membershipLoaded = true;
            evaluateAccessAndRender();
        } else {
            memberReg = MembershipRepository.listenMine(clubId, profile.getUid(),
                    new MembershipRepository.MyMembershipListener() {
                        @Override
                        public void onChanged(@Nullable ClubMember member) {
                            myMembership = member;
                            membershipLoaded = true;
                            evaluateAccessAndRender();
                        }

                        @Override
                        public void onError(String message) {
                            membershipLoaded = true;
                            evaluateAccessAndRender();
                        }
                    });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (clubReg != null) {
            clubReg.remove();
            clubReg = null;
        }
        if (memberReg != null) {
            memberReg.remove();
            memberReg = null;
        }
        stopChatListener();
    }

    private void stopChatListener() {
        if (chatHandle != null) {
            chatHandle.remove();
            chatHandle = null;
        }
    }

    private boolean canAccessChat() {
        if (profile == null) return false;
        if (profile.getRoleEnum() == Role.ADMIN) return true;
        if (currentClub != null && currentClub.hasOrganizer(profile.getUid())) return true;
        return myMembership != null && ClubMember.APPROVED.equals(myMembership.getStatus());
    }

    private boolean isClubManager() {
        if (profile == null) return false;
        if (profile.getRoleEnum() == Role.ADMIN) return true;
        return currentClub != null && currentClub.hasOrganizer(profile.getUid());
    }

    private String myRoleBadge() {
        if (profile.getRoleEnum() == Role.ADMIN) return "Admin";
        if (currentClub != null && currentClub.hasOrganizer(profile.getUid())) return "Organizer";
        return "Member";
    }

    private void evaluateAccessAndRender() {
        if (binding == null) return;
        if (!clubLoaded && !membershipLoaded) {
            return;
        }
        boolean allowed = canAccessChat();
        if (!allowed) {
            stopChatListener();
            binding.panelLocked.setVisibility(View.VISIBLE);
            binding.recyclerMessages.setVisibility(View.GONE);
            binding.emptyChat.setVisibility(View.GONE);
            binding.composerBar.setVisibility(View.GONE);
            if (myMembership != null && ClubMember.PENDING.equals(myMembership.getStatus())) {
                binding.tvLockedReason.setText("Your membership application is currently pending organizer approval. Once approved, you'll be able to join the club group chat.");
            } else {
                binding.tvLockedReason.setText("Only approved members of this club can access and chat in this group room. Apply to join the club on the club page first!");
            }
            return;
        }

        binding.panelLocked.setVisibility(View.GONE);
        binding.composerBar.setVisibility(View.VISIBLE);

        if (chatHandle == null) {
            chatHandle = ClubChatRepository.listen(this, clubId, new ClubChatRepository.MessagesListener() {
                @Override
                public void onMessages(List<ChatMessage> list) {
                    if (isDestroyed() || binding == null) return;
                    messages.clear();
                    messages.addAll(list);
                    adapter.submit(messages);
                    boolean empty = messages.isEmpty();
                    binding.emptyChat.setVisibility(empty ? View.VISIBLE : View.GONE);
                    binding.recyclerMessages.setVisibility(empty ? View.GONE : View.VISIBLE);
                    if (!empty) {
                        binding.recyclerMessages.scrollToPosition(messages.size() - 1);
                    }
                }

                @Override
                public void onError(String message) {
                    if (!isDestroyed() && binding != null) {
                        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void sendMessage() {
        if (!canAccessChat()) {
            Snackbar.make(binding.getRoot(),
                    "Only approved club members can send messages.", Snackbar.LENGTH_SHORT).show();
            return;
        }
        String text = binding.etMessage.getText() == null ? ""
                : binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        binding.etMessage.setText("");
        ClubChatRepository.send(this, clubId, profile, myRoleBadge(), text, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Realtime listener updates automatically
            }

            @Override
            public void onError(String message) {
                if (!isDestroyed() && binding != null) {
                    Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void confirmDeleteMessage(ChatMessage msg) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete message?")
                .setMessage("Remove this message from the club group chat?")
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) ->
                        ClubChatRepository.delete(this, clubId, msg.getId(), new Callback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                            }

                            @Override
                            public void onError(String message) {
                            }
                        }))
                .show();
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Holder> {
        private List<ChatMessage> items = new ArrayList<>();

        void submit(List<ChatMessage> list) {
            this.items = new ArrayList<>(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemChatMessageBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ChatMessage m = items.get(position);
            boolean mine = profile != null && profile.getUid().equals(m.getSenderUid());
            String timeStr = m.getTimestamp() > 0
                    ? TimeFormats.time(new Date(m.getTimestamp())) : "";

            if (mine) {
                holder.binding.rowOutgoing.setVisibility(View.VISIBLE);
                holder.binding.rowIncoming.setVisibility(View.GONE);
                holder.binding.tvOutgoingText.setText(m.getText());
                holder.binding.tvOutgoingTime.setText(timeStr);
                holder.binding.cardOutgoing.setOnLongClickListener(v -> {
                    confirmDeleteMessage(m);
                    return true;
                });
            } else {
                holder.binding.rowIncoming.setVisibility(View.VISIBLE);
                holder.binding.rowOutgoing.setVisibility(View.GONE);
                String sName = m.getSenderName() == null || m.getSenderName().isEmpty()
                        ? "Member" : m.getSenderName();
                holder.binding.tvSenderAvatar.setText(
                        sName.substring(0, 1).toUpperCase(Locale.ROOT));
                holder.binding.tvSenderName.setText(sName);
                String dept = m.getSenderDepartment() == null ? "" : m.getSenderDepartment();
                String role = m.getSenderRole() == null ? "Member" : m.getSenderRole();
                holder.binding.tvSenderBadge.setText(dept.isEmpty() ? role : dept + " • " + role);
                holder.binding.tvIncomingText.setText(m.getText());
                holder.binding.tvIncomingTime.setText(timeStr);
                holder.binding.cardIncoming.setOnLongClickListener(v -> {
                    if (isClubManager()) {
                        confirmDeleteMessage(m);
                        return true;
                    }
                    return false;
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemChatMessageBinding binding;
            Holder(ItemChatMessageBinding b) {
                super(b.getRoot());
                this.binding = b;
            }
        }
    }
}

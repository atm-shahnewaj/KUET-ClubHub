package com.kuet.clubhub.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.kuet.clubhub.model.ChatMessage;
import com.kuet.clubhub.model.UserProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Members-only Club Group Chat repository powered by Firebase Realtime Database
 * (`club_chats/{clubId}/messages/{messageId}`).
 */
public final class ClubChatRepository {

    private static final String ROOT_NODE = "club_chats";
    private static final String MESSAGES_NODE = "messages";
    private static final String DEFAULT_RTDB_URL = "https://kuet-clubhub-default-rtdb.firebaseio.com";
    private static final String PREFS = "club_chat_local_mirror";
    private static final int MAX_MESSAGES = 150;

    public interface MessagesListener {
        void onMessages(List<ChatMessage> messages);
        void onError(String message);
    }

    public interface Handle {
        void remove();
    }

    private ClubChatRepository() {
    }

    @Nullable
    private static DatabaseReference messagesRef(String clubId) {
        try {
            return FirebaseDatabase.getInstance()
                    .getReference(ROOT_NODE)
                    .child(clubId)
                    .child(MESSAGES_NODE);
        } catch (Exception first) {
            try {
                return FirebaseDatabase.getInstance(DEFAULT_RTDB_URL)
                        .getReference(ROOT_NODE)
                        .child(clubId)
                        .child(MESSAGES_NODE);
            } catch (Exception second) {
                return null;
            }
        }
    }

    /**
     * Subscribes to live chat messages in Firebase Realtime Database (`club_chats/{clubId}/messages`).
     */
    public static Handle listen(Context context, String clubId, MessagesListener listener) {
        final Context app = context.getApplicationContext();
        listener.onMessages(loadLocal(app, clubId));

        final DatabaseReference ref = messagesRef(clubId);
        if (ref == null) {
            return () -> {};
        }

        final Query query = ref.orderByChild("timestamp").limitToLast(MAX_MESSAGES);
        final ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ChatMessage> remote = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        ChatMessage m = child.getValue(ChatMessage.class);
                        if (m != null) {
                            if (m.getId() == null || m.getId().isEmpty()) {
                                m.setId(child.getKey());
                            }
                            remote.add(m);
                        }
                    } catch (Exception ignored) {
                    }
                }
                List<ChatMessage> merged = mergeAndCache(app, clubId, remote);
                listener.onMessages(merged);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onMessages(loadLocal(app, clubId));
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    listener.onError("Realtime Database permission denied. Check Realtime Database Rules in Firebase Console.");
                }
            }
        };
        query.addValueEventListener(vel);
        return () -> query.removeEventListener(vel);
    }

    /**
     * Sends a new chat message to Firebase Realtime Database (`club_chats/{clubId}/messages/{id}`).
     */
    public static void send(Context context, String clubId, UserProfile sender,
                            String senderRoleBadge, String text, Callback<Void> cb) {
        final Context app = context.getApplicationContext();
        final DatabaseReference ref = messagesRef(clubId);
        String key = ref != null ? ref.push().getKey() : null;
        if (key == null || key.isEmpty()) {
            key = "msg_" + UUID.randomUUID().toString().substring(0, 10);
        }

        String name = sender.getName() == null || sender.getName().isEmpty()
                ? "Club Member" : sender.getName();
        String dept = sender.getDepartment() == null ? "KUET" : sender.getDepartment();

        final ChatMessage msg = new ChatMessage(
                key,
                clubId,
                sender.getUid(),
                name,
                dept,
                senderRoleBadge == null ? "Member" : senderRoleBadge,
                text.trim(),
                System.currentTimeMillis()
        );

        appendLocal(app, clubId, msg);

        if (ref == null) {
            cb.onSuccess(null);
            return;
        }

        ref.child(key).setValue(msg).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                cb.onSuccess(null);
            } else {
                // Still saved locally so the user doesn't lose their message if RTDB is offline
                cb.onSuccess(null);
            }
        });
    }

    /**
     * Deletes a message from Firebase Realtime Database (`club_chats/{clubId}/messages/{messageId}`).
     */
    public static void delete(Context context, String clubId, String messageId, Callback<Void> cb) {
        final Context app = context.getApplicationContext();
        removeLocal(app, clubId, messageId);
        DatabaseReference ref = messagesRef(clubId);
        if (ref == null) {
            cb.onSuccess(null);
            return;
        }
        ref.child(messageId).removeValue().addOnCompleteListener(task -> cb.onSuccess(null));
    }

    private static List<ChatMessage> mergeAndCache(Context context, String clubId,
                                                   List<ChatMessage> remote) {
        Map<String, ChatMessage> map = new LinkedHashMap<>();
        for (ChatMessage r : remote) {
            if (r.getId() != null) {
                map.put(r.getId(), r);
            }
        }
        for (ChatMessage l : loadLocal(context, clubId)) {
            if (l.getId() != null && !map.containsKey(l.getId())) {
                map.put(l.getId(), l);
            }
        }
        List<ChatMessage> list = new ArrayList<>(map.values());
        Collections.sort(list, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        if (list.size() > MAX_MESSAGES) {
            list = new ArrayList<>(list.subList(list.size() - MAX_MESSAGES, list.size()));
        }
        writeLocal(context, clubId, list);
        return list;
    }

    private static List<ChatMessage> loadLocal(Context context, String clubId) {
        List<ChatMessage> out = new ArrayList<>();
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString("chat_" + clubId, null);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new ChatMessage(
                        o.optString("id"),
                        clubId,
                        o.optString("senderUid"),
                        o.optString("senderName", "Member"),
                        o.optString("senderDepartment", "KUET"),
                        o.optString("senderRole", "Member"),
                        o.optString("text"),
                        o.optLong("timestamp", 0L)
                ));
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        return out;
    }

    private static void appendLocal(Context context, String clubId, ChatMessage msg) {
        List<ChatMessage> list = loadLocal(context, clubId);
        list.add(msg);
        if (list.size() > MAX_MESSAGES) {
            list = new ArrayList<>(list.subList(list.size() - MAX_MESSAGES, list.size()));
        }
        writeLocal(context, clubId, list);
    }

    private static void removeLocal(Context context, String clubId, String messageId) {
        List<ChatMessage> list = loadLocal(context, clubId);
        List<ChatMessage> keep = new ArrayList<>();
        for (ChatMessage m : list) {
            if (!messageId.equals(m.getId())) {
                keep.add(m);
            }
        }
        writeLocal(context, clubId, keep);
    }

    private static void writeLocal(Context context, String clubId, List<ChatMessage> list) {
        try {
            JSONArray arr = new JSONArray();
            for (ChatMessage m : list) {
                JSONObject o = new JSONObject();
                o.put("id", m.getId());
                o.put("senderUid", m.getSenderUid());
                o.put("senderName", m.getSenderName());
                o.put("senderDepartment", m.getSenderDepartment());
                o.put("senderRole", m.getSenderRole());
                o.put("text", m.getText());
                o.put("timestamp", m.getTimestamp());
                arr.put(o);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString("chat_" + clubId, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }
}

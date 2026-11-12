package com.kuet.clubhub.notify;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.kuet.clubhub.data.local.AlertEntity;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.util.Io;

/**
 * Centralized Toast and in-app event notification dispatcher for KUET ClubHub.
 *
 * Ensures every key campus event, status change, and user action shows a clear, immediate
 * {@link Toast} on the main thread, while optionally recording significant real-time events
 * into the in-app Alerts feed and posting a system notification when backgrounded.
 */
public final class ToastCenter {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Toast activeToast;

    private ToastCenter() {
    }

    public static void show(@Nullable Context context, @Nullable CharSequence message) {
        show(context, message, Toast.LENGTH_LONG);
    }

    public static void showShort(@Nullable Context context, @Nullable CharSequence message) {
        show(context, message, Toast.LENGTH_SHORT);
    }

    public static void show(@Nullable Context context, @StringRes int resId) {
        if (context == null) {
            return;
        }
        show(context, context.getString(resId), Toast.LENGTH_LONG);
    }

    public static void showShort(@Nullable Context context, @StringRes int resId) {
        if (context == null) {
            return;
        }
        show(context, context.getString(resId), Toast.LENGTH_SHORT);
    }

    public static void show(@Nullable final Context context,
                            @Nullable final CharSequence message,
                            final int duration) {
        if (context == null || message == null || message.length() == 0) {
            return;
        }
        final Context app = context.getApplicationContext();
        Runnable task = () -> {
            try {
                if (activeToast != null) {
                    activeToast.cancel();
                }
            } catch (Exception ignored) {
            }
            activeToast = Toast.makeText(app, message, duration);
            activeToast.show();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            MAIN.post(task);
        }
    }

    // ------------------------------------------------------------------ Named Event Toast Helpers

    /** 1. Waitlist promoted: “A spot opened. You’re now registered for <event>.” */
    public static void waitlistPromoted(@NonNull Context context, @Nullable String eventTitle,
                                        @Nullable String eventId) {
        String title = safe(eventTitle, "this event");
        String msg = "A spot opened. You\u2019re now registered for " + title + ".";
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context, "waitlist_promoted_" + safeId(eventId, title),
                "Waitlist Promoted: " + title, msg, eventId);
    }

    /** 2. Registration confirmed: After you register, confirm it, optionally with the QR pass. */
    public static void registrationConfirmed(@NonNull Context context, @Nullable String eventTitle,
                                             @Nullable String passCode, boolean waitlisted) {
        String title = safe(eventTitle, "this event");
        String msg;
        if (waitlisted) {
            msg = "Added to the waitlist for " + title
                    + ". We\u2019ll notify you if a spot opens!";
        } else if (passCode != null && !passCode.trim().isEmpty()) {
            msg = "Registration confirmed for " + title
                    + "! Your QR pass (" + passCode.trim() + ") is ready.";
        } else {
            msg = "Registration confirmed for " + title + "! Your QR pass is ready.";
        }
        show(context, msg, Toast.LENGTH_LONG);
    }

    /** 3. Club join approved or rejected: To the student. */
    public static void clubJoinDecision(@NonNull Context context, @Nullable String clubName,
                                        boolean approved, @Nullable String clubId) {
        String club = safe(clubName, "the club");
        String msg = approved
                ? "Your request to join " + club + " was approved! Welcome aboard."
                : "Your membership request for " + club + " was declined.";
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context,
                "club_decision_" + safeId(clubId, club) + "_" + (approved ? "approved" : "rejected"),
                (approved ? "Club Membership Approved: " : "Club Application Update: ") + club,
                msg, null);
    }

    /** 4. New join request: To club organizers. */
    public static void newClubJoinRequest(@NonNull Context context, @Nullable String studentName,
                                          @Nullable String clubName, @Nullable String clubId,
                                          @Nullable String studentUid) {
        String student = safe(studentName, "A student");
        String club = safe(clubName, "your club");
        String msg = "New join request: " + student + " applied to join " + club + ".";
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context,
                "join_req_" + safeId(clubId, club) + "_" + safeId(studentUid, student),
                "New Join Request · " + club, msg, null);
    }

    /** 5. Event updated or cancelled: If the time, venue or status changes after you registered. */
    public static void eventUpdatedOrCancelled(@NonNull Context context, @Nullable String eventTitle,
                                               @Nullable String changeSummary, boolean cancelled,
                                               @Nullable String eventId) {
        String title = safe(eventTitle, "an event you registered for");
        String msg;
        if (cancelled) {
            msg = "Event cancelled: " + title + " has been cancelled by the organizers.";
        } else if (changeSummary != null && !changeSummary.isEmpty()) {
            msg = "Event updated: " + title + " (" + changeSummary + ").";
        } else {
            msg = "Event updated: Time, venue, or details changed for " + title + ".";
        }
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context,
                "event_change_" + safeId(eventId, title) + "_" + System.currentTimeMillis(),
                (cancelled ? "Event Cancelled: " : "Event Updated: ") + title, msg,
                cancelled ? null : eventId);
    }

    /** 6. New club notice or pinned notice: To members of that club. */
    public static void newClubNotice(@NonNull Context context, @Nullable String clubName,
                                     @Nullable String noticeTitle, boolean pinned,
                                     @Nullable String noticeId) {
        String club = safe(clubName, "Club");
        String nTitle = safe(noticeTitle, "New announcement");
        String prefix = pinned ? "New pinned notice in " : "New notice in ";
        String msg = prefix + club + ": \u201C" + nTitle + "\u201D";
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context, "notice_" + safeId(noticeId, nTitle),
                (pinned ? "Pinned Notice · " : "Club Notice · ") + club, msg, null);
    }

    /** 7. Group chat messages or mentions: Notifications & toasts for club chat, respecting per-club mute. */
    public static void clubChatMessage(@NonNull Context context, @Nullable String clubId,
                                       @Nullable String clubName, @Nullable String senderName,
                                       @Nullable String text, boolean mentioned,
                                       @Nullable String messageId) {
        String club = safe(clubName, "Club Chat");
        String sender = safe(senderName, "Member");
        String snippet = safe(text, "");
        if (snippet.length() > 60) {
            snippet = snippet.substring(0, 57) + "...";
        }
        String msg = mentioned
                ? sender + " mentioned you in " + club + ": \u201C" + snippet + "\u201D"
                : club + " · " + sender + ": " + snippet;
        show(context, msg, Toast.LENGTH_LONG);
        int notifId = ("chat_" + safeId(clubId, club) + "_" + safeId(messageId, snippet)).hashCode();
        NotificationHelper.show(context.getApplicationContext(), NotificationHelper.CHANNEL_ALERTS,
                notifId, (mentioned ? "Mentioned in " : "New message in ") + club, msg,
                NotificationHelper.clubChatPendingIntent(context.getApplicationContext(),
                        clubId, club, notifId));
    }

    /** 8a. Team invite received: To the invited student. */
    public static void teamInviteReceived(@NonNull Context context, @Nullable String fromName,
                                          @Nullable String teamName, @Nullable String eventTitle,
                                          @Nullable String eventId, @Nullable String teamId) {
        String leader = safe(fromName, "A team leader");
        String team = safe(teamName, "a team");
        String event = safe(eventTitle, "competition");
        String msg = "Team invite: " + leader + " invited you to join \u201C" + team
                + "\u201D for " + event + ".";
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context, "team_invite_" + safeId(teamId, team),
                "Team Invite · " + team, msg, eventId);
    }

    /** 8b. Team member joined or seeker request: To the team leader / members. */
    public static void teamMemberJoinedOrRequest(@NonNull Context context, @Nullable String studentName,
                                                 @Nullable String teamName, boolean isJoinRequest,
                                                 @Nullable String eventId) {
        String person = safe(studentName, "A student");
        String team = safe(teamName, "your team");
        String msg = isJoinRequest
                ? person + " is looking for a team in " + team + "."
                : person + " joined team \u201C" + team + "\u201D!";
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context,
                "team_update_" + safeId(eventId, team) + "_" + safeId(person, "m"),
                "Team Update · " + team, msg, eventId);
    }

    /** 9. Certificate ready: “Your certificate for <event> is available.” */
    public static void certificateReady(@NonNull Context context, @Nullable String eventTitle,
                                        @Nullable String eventId) {
        String title = safe(eventTitle, "this event");
        String msg = "Your certificate for " + title + " is available.";
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context, "cert_ready_" + safeId(eventId, title),
                "Certificate Ready", msg, eventId);
    }

    /** 10. Live poll or Q&A started: For people currently registered or checked in. */
    public static void livePollOrQaStarted(@NonNull Context context, @Nullable String eventTitle,
                                           @Nullable String detail, boolean isPoll,
                                           @Nullable String eventId, @Nullable String itemId) {
        String title = safe(eventTitle, "this event");
        String msg;
        if (isPoll) {
            msg = "Live poll started in " + title
                    + (detail != null && !detail.isEmpty() ? ": \u201C" + detail + "\u201D" : "!");
        } else {
            msg = "Live Q&A question posted in " + title
                    + (detail != null && !detail.isEmpty() ? ": \u201C" + detail + "\u201D" : "!");
        }
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context,
                (isPoll ? "poll_" : "qa_") + safeId(eventId, title) + "_" + safeId(itemId, "live"),
                (isPoll ? "Live Poll Started · " : "Live Q&A Active · ") + title, msg, eventId);
    }

    /** 11. Feedback prompt: After an event ends: “How was <event>?” */
    public static void feedbackPrompt(@NonNull Context context, @Nullable String eventTitle,
                                      @Nullable String eventId) {
        String title = safe(eventTitle, "the event");
        String msg = "How was " + title + "? Tap Rate Event to share your feedback.";
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context, "feedback_prompt_" + safeId(eventId, title),
                "How was " + title + "?", msg, eventId);
    }

    /** 12. Event starting, check-in open: For organizers and attendees. */
    public static void eventStartingOrCheckInOpen(@NonNull Context context,
                                                  @Nullable String eventTitle,
                                                  boolean checkInOpenNow,
                                                  boolean forOrganizer,
                                                  @Nullable String eventId) {
        String title = safe(eventTitle, "your event");
        String msg;
        if (checkInOpenNow) {
            msg = forOrganizer
                    ? "Check-in is now open for " + title + ". Attendees can scan your live QR code."
                    : "Event starting & check-in is now open for " + title + "! Scan the QR code at the venue.";
        } else {
            msg = forOrganizer
                    ? title + " is starting now! Open the dashboard to start QR check-in."
                    : title + " is starting now! Check-in is open for attendees.";
        }
        show(context, msg, Toast.LENGTH_LONG);
        recordAndNotify(context,
                "checkin_open_" + safeId(eventId, title) + "_" + (checkInOpenNow ? "qr" : "start"),
                "Check-In Open · " + title, msg, eventId);
    }

    private static void recordAndNotify(@NonNull Context context, @NonNull final String alertKey,
                                        @NonNull final String title, @NonNull final String message,
                                        @Nullable final String eventId) {
        final Context app = context.getApplicationContext();
        Io.DISK.execute(() -> {
            String targetId = (eventId != null && !eventId.isEmpty()) ? eventId : "info-" + alertKey;
            AlertEntity entity = AlertEntity.create(alertKey, title, message);
            long row = AppDatabase.getInstance(app).alertDao().insertIgnore(entity);
            if (row == -1) {
                return;
            }
            int notifId = alertKey.hashCode();
            NotificationHelper.show(app, NotificationHelper.CHANNEL_ALERTS, notifId, title, message,
                    eventId != null && !eventId.isEmpty()
                            ? NotificationHelper.eventPendingIntent(app, eventId, notifId)
                            : NotificationHelper.alertsPendingIntent(app, notifId));
        });
    }

    private static String safe(@Nullable String s, @NonNull String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }

    private static String safeId(@Nullable String id, @NonNull String fallback) {
        String raw = (id == null || id.trim().isEmpty()) ? fallback : id.trim();
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}

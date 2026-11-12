package com.kuet.clubhub.notify;

import android.content.Context;

import androidx.annotation.Nullable;

/**
 * Live status of the interest-alert foreground service. The service, the alert processor and the
 * background worker report into it, and every change re-posts the ongoing notification in the
 * notification tray (see {@link NotificationHelper#updateWatcher(Context)}).
 */
public final class WatcherStatus {

    public enum State { CONNECTING, LIVE, OFFLINE }

    /** An immutable copy of the status, safe to read from any thread. */
    public static final class Snapshot {
        public final State state;
        public final long startedAt;
        public final int matches;
        @Nullable public final String lastTitle;
        public final long lastCheckedAt;

        Snapshot(State state, long startedAt, int matches, @Nullable String lastTitle,
                 long lastCheckedAt) {
            this.state = state;
            this.startedAt = startedAt;
            this.matches = matches;
            this.lastTitle = lastTitle;
            this.lastCheckedAt = lastCheckedAt;
        }
    }

    private static boolean running = false;
    private static State state = State.CONNECTING;
    private static long startedAt = System.currentTimeMillis();
    private static int matches = 0;
    private static String lastTitle = null;
    private static long lastCheckedAt = 0;

    private WatcherStatus() {
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(state, startedAt, matches, lastTitle, lastCheckedAt);
    }

    /** The service came up. A repeated start of a service that is already running changes nothing. */
    static void onServiceStarted() {
        synchronized (WatcherStatus.class) {
            if (running) {
                return;
            }
            running = true;
            state = State.CONNECTING;
            startedAt = System.currentTimeMillis();
            matches = 0;
            lastTitle = null;
            lastCheckedAt = 0;
        }
    }

    static void onServiceStopped() {
        synchronized (WatcherStatus.class) {
            running = false;
        }
    }

    /** The Firestore listener delivered a snapshot ({@code error} true means it failed). */
    static void onListenerResult(Context context, boolean error, boolean fromCache) {
        synchronized (WatcherStatus.class) {
            if (error) {
                state = State.OFFLINE;
            } else if (!fromCache) {
                state = State.LIVE;
                lastCheckedAt = System.currentTimeMillis();
            } else {
                return; // a cache-only snapshot says nothing new about the connection
            }
        }
        push(context);
    }

    /** A matching event was found and stored as an alert. */
    public static void onAlert(Context context, String title) {
        synchronized (WatcherStatus.class) {
            matches++;
            lastTitle = title;
            lastCheckedAt = System.currentTimeMillis();
        }
        push(context);
    }

    private static void push(Context context) {
        boolean isRunning;
        synchronized (WatcherStatus.class) {
            isRunning = running;
        }
        if (isRunning) {
            NotificationHelper.updateWatcher(context.getApplicationContext());
        }
    }
}

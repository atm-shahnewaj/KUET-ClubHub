package com.kuet.clubhub.util;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Shared background executors for small disk jobs and HTTP requests. */
public final class Io {

    /** Single background thread for local disk jobs (Room writes, reminder scheduling, exports). */
    public static final Executor DISK = Executors.newSingleThreadExecutor();

    /** Background thread pool for short HTTP network requests (weather forecast). */
    public static final Executor NETWORK = Executors.newCachedThreadPool();

    private Io() {
    }
}

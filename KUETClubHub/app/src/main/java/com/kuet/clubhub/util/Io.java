package com.kuet.clubhub.util;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** One shared background thread for small disk jobs (Room writes, reminder scheduling). */
public final class Io {

    public static final Executor DISK = Executors.newSingleThreadExecutor();

    private Io() {
    }
}

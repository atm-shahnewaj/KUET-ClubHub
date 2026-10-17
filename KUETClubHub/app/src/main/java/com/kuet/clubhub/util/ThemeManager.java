package com.kuet.clubhub.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/** Remembers and applies the light / dark / system appearance choice. */
public final class ThemeManager {

    private static final String PREFS = "clubhub_settings";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemeManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** One of AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM / MODE_NIGHT_NO / MODE_NIGHT_YES. */
    public static int getMode(Context context) {
        return prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /** Saves the choice and applies it (visible activities recreate themselves). */
    public static void setMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_NIGHT_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static void applySaved(Context context) {
        AppCompatDelegate.setDefaultNightMode(getMode(context));
    }

    /** True when the screen is currently drawn in dark mode (whatever the reason). */
    public static boolean isNight(Context context) {
        int flags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Quick switch used by the toolbar button: flips light <-> dark. */
    public static void toggle(Context context) {
        setMode(context, isNight(context)
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES);
    }
}

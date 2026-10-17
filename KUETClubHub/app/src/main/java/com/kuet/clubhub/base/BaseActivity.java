package com.kuet.clubhub.base;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Shared base for every Activity. It logs all lifecycle callbacks to Logcat
 * (filter by the tag "ClubHubLifecycle") and handles edge-to-edge window insets.
 */
public abstract class BaseActivity extends AppCompatActivity {

    public static final String LIFECYCLE_TAG = "ClubHubLifecycle";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        logLifecycle("onCreate" + (savedInstanceState == null ? "" : " (restored state)"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        logLifecycle("onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        logLifecycle("onResume");
    }

    @Override
    protected void onPause() {
        logLifecycle("onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        logLifecycle("onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        logLifecycle("onDestroy");
        super.onDestroy();
    }

    private void logLifecycle(String callback) {
        Log.d(LIFECYCLE_TAG, getClass().getSimpleName() + ": " + callback);
    }

    /** Pads the root view so content stays clear of system bars, cutouts and the keyboard. */
    protected void applyInsets(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Like applyInsets, but lets a coloured header run under the status bar: the root gets the
     * side/bottom/keyboard padding and the header gets extra top padding for the status bar.
     */
    protected void applyInsetsWithHeader(@NonNull View root, @NonNull View header) {
        final int headerBaseTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            Insets all = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            v.setPadding(all.left, 0, all.right, all.bottom);
            header.setPadding(header.getPaddingLeft(), headerBaseTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Status-bar icons in white, for screens whose top area is the green header. */
    protected void useWhiteStatusBarIcons() {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
    }

    /** Starts the target as a fresh task (used after login / logout). */
    protected void startClearTask(@NonNull Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

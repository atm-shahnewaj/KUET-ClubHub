package com.kuet.clubhub.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kuet.clubhub.R;
import com.kuet.clubhub.notify.RealtimeToastWatcher;
import com.kuet.clubhub.util.ThemeManager;

/**
 * Shared base for every Activity. It logs all lifecycle callbacks to Logcat
 * (filter by the tag "ClubHubLifecycle"), handles edge-to-edge window insets,
 * and keeps the real-time event/club toast watcher active while the app is open.
 */
public abstract class BaseActivity extends AppCompatActivity {

    public static final String LIFECYCLE_TAG = "ClubHubLifecycle";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        logLifecycle("onCreate" + (savedInstanceState == null ? "" : " (restored state)"));
    }

    // ------------------------------------------------------------ airplane mode receiver

    private AlertDialog airplaneDialog;

    /** Runtime-registered: ACTION_AIRPLANE_MODE_CHANGED cannot be declared in the manifest. */
    private final BroadcastReceiver airplaneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
                return;
            }
            boolean on = intent.hasExtra("state")
                    ? intent.getBooleanExtra("state", false)
                    : Settings.Global.getInt(getContentResolver(),
                            Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
            if (on) {
                showAirplaneOnDialog();
            } else {
                dismissAirplaneDialog();
                Toast.makeText(BaseActivity.this, R.string.airplane_off_toast,
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    private void showAirplaneOnDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        dismissAirplaneDialog();
        airplaneDialog = new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_airplane_mode)
                .setTitle(R.string.airplane_on_title)
                .setMessage(R.string.airplane_on_message)
                .setPositiveButton(R.string.airplane_keep_offline, null)
                .setNeutralButton(R.string.airplane_open_settings, (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
                    } catch (Exception ignored) {
                        startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                    }
                })
                .show();
    }

    private void dismissAirplaneDialog() {
        if (airplaneDialog != null && airplaneDialog.isShowing()) {
            airplaneDialog.dismiss();
        }
        airplaneDialog = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, airplaneReceiver,
                new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED);
        RealtimeToastWatcher.getInstance(this).onActivityStarted(this);
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
        RealtimeToastWatcher.getInstance(this).onActivityStopped();
        try {
            unregisterReceiver(airplaneReceiver);
        } catch (IllegalArgumentException ignored) {
            // not registered
        }
        dismissAirplaneDialog();
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
     * Like applyInsets, but lets a header area run under the status bar: the root gets the
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

    /**
     * Configures status-bar icon appearance to match the unified Mint-Surface header
     * (dark icons in light mode, light icons in dark mode).
     */
    protected void useWhiteStatusBarIcons() {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!ThemeManager.isNight(this));
    }

    /** Starts the target as a fresh task (used after login / logout). */
    protected void startClearTask(@NonNull Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

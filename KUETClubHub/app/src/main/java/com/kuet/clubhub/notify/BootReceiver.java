package com.kuet.clubhub.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.kuet.clubhub.util.Io;

/** Alarms disappear on reboot or app update, so rebuild them from the stored events. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        Io.DISK.execute(() -> {
            try {
                ReminderScheduler.rescheduleAll(app);
                AlertController.restartBackground(app);
            } finally {
                pending.finish();
            }
        });
    }
}

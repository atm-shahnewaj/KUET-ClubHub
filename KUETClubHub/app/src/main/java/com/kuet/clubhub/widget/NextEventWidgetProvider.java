package com.kuet.clubhub.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.kuet.clubhub.R;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.data.local.ScheduledEventEntity;
import com.kuet.clubhub.ui.LauncherActivity;
import com.kuet.clubhub.ui.MainActivity;
import com.kuet.clubhub.ui.events.EventDetailActivity;
import com.kuet.clubhub.util.CountdownText;
import com.kuet.clubhub.util.Io;
import com.kuet.clubhub.util.TimeFormats;
import com.kuet.clubhub.util.WidgetEventPicker;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home screen widget showing the student's next registered campus event with a countdown badge
 * (Proposal features 17 and 41). Refreshed automatically whenever registrations sync in Room or
 * when the launcher requests an update.
 */
public class NextEventWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        refreshAll(context);
    }

    /** Updates every placed instance of the widget from the local Room schedule cache. */
    public static void refreshAll(Context context) {
        final Context app = context.getApplicationContext();
        Io.DISK.execute(() -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(app);
            ComponentName component = new ComponentName(app, NextEventWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(component);
            if (ids == null || ids.length == 0) {
                return;
            }
            List<ScheduledEventEntity> rows = AppDatabase.getInstance(app).scheduleDao().getAll();
            long now = System.currentTimeMillis();
            ScheduledEventEntity next = WidgetEventPicker.pickNext(rows, now);
            RemoteViews views = buildRemoteViews(app, next, now);
            manager.updateAppWidget(ids, views);
        });
    }

    private static RemoteViews buildRemoteViews(Context app, ScheduledEventEntity next, long now) {
        RemoteViews views = new RemoteViews(app.getPackageName(), R.layout.widget_next_event);
        Intent launch = new Intent(app, LauncherActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (next != null) {
            Date start = new Date(next.startMillis);
            String title = next.title == null || next.title.isEmpty()
                    ? app.getString(R.string.title_event) : next.title;
            String countdown = CountdownText.formatWidget(next.startMillis, next.endMillis, now);
            String time = TimeFormats.time(start);
            String meta = next.venue == null || next.venue.trim().isEmpty()
                    ? time : time + " · " + next.venue.trim();

            views.setTextViewText(R.id.tv_widget_header, app.getString(R.string.widget_label_next));
            views.setTextViewText(R.id.tv_widget_month,
                    TimeFormats.monthShort(start).toUpperCase(Locale.getDefault()));
            views.setTextViewText(R.id.tv_widget_day, TimeFormats.dayNumber(start));
            views.setTextViewText(R.id.tv_widget_title, title);
            views.setTextViewText(R.id.tv_widget_meta, meta);
            views.setTextViewText(R.id.tv_widget_countdown, countdown);
            views.setViewVisibility(R.id.tv_widget_month, View.VISIBLE);
            views.setViewVisibility(R.id.tv_widget_day, View.VISIBLE);
            views.setViewVisibility(R.id.iv_widget_icon, View.GONE);
            views.setViewVisibility(R.id.tv_widget_countdown, View.VISIBLE);

            launch.setAction(MainActivity.ACTION_OPEN_EVENT);
            launch.putExtra(EventDetailActivity.EXTRA_EVENT_ID, next.eventId);
        } else {
            views.setTextViewText(R.id.tv_widget_header, app.getString(R.string.widget_label_empty));
            views.setTextViewText(R.id.tv_widget_title, app.getString(R.string.widget_empty_title));
            views.setTextViewText(R.id.tv_widget_meta, app.getString(R.string.widget_empty_subtitle));
            views.setViewVisibility(R.id.tv_widget_month, View.GONE);
            views.setViewVisibility(R.id.tv_widget_day, View.GONE);
            views.setViewVisibility(R.id.iv_widget_icon, View.VISIBLE);
            views.setViewVisibility(R.id.tv_widget_countdown, View.GONE);
        }

        PendingIntent pi = PendingIntent.getActivity(app, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);
        return views;
    }
}

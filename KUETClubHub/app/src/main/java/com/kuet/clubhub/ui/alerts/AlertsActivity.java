package com.kuet.clubhub.ui.alerts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.local.AlertDao;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.databinding.ActivityAlertsBinding;
import com.kuet.clubhub.notify.ToastCenter;
import com.kuet.clubhub.ui.events.EventDetailActivity;
import com.kuet.clubhub.util.Io;

import java.util.ArrayList;

/** Alerts about new events that match the student's interests and real-time campus notifications. */
public class AlertsActivity extends BaseActivity {

    private ActivityAlertsBinding binding;
    private AlertDao dao;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAlertsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        dao = AppDatabase.getInstance(this).alertDao();

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.alerts_toolbar);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_alerts) {
                Io.DISK.execute(dao::dismissAll);
                ToastCenter.showShort(this, "All alerts cleared.");
                return true;
            }
            return false;
        });

        AlertAdapter adapter = new AlertAdapter(alert -> {
            String id = alert.eventId == null ? "" : alert.eventId;
            if (!id.isEmpty() && !id.startsWith("test-")
                    && !id.startsWith("waitlist_promoted_")
                    && !id.startsWith("club_decision_")
                    && !id.startsWith("join_req_")
                    && !id.startsWith("event_change_")
                    && !id.startsWith("notice_")
                    && !id.startsWith("team_invite_")
                    && !id.startsWith("team_update_")
                    && !id.startsWith("cert_ready_")
                    && !id.startsWith("poll_")
                    && !id.startsWith("qa_")
                    && !id.startsWith("feedback_prompt_")
                    && !id.startsWith("checkin_open_")) {
                Intent intent = new Intent(this, EventDetailActivity.class);
                intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, id);
                startActivity(intent);
            } else {
                ToastCenter.showShort(this, alert.message);
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        dao.observeVisible().observe(this, alerts -> {
            adapter.submitList(new ArrayList<>(alerts));
            binding.emptyContainer.setVisibility(alerts.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Leaving the screen counts as having read everything on it.
        Io.DISK.execute(dao::markAllSeen);
    }
}

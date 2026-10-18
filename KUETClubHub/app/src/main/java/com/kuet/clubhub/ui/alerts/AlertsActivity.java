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
import com.kuet.clubhub.ui.events.EventDetailActivity;
import com.kuet.clubhub.util.Io;

import java.util.ArrayList;

/** Alerts about new events that match the student's interests. */
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
                return true;
            }
            return false;
        });

        AlertAdapter adapter = new AlertAdapter(alert -> {
            if (!alert.eventId.startsWith("test-")) {
                Intent intent = new Intent(this, EventDetailActivity.class);
                intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, alert.eventId);
                startActivity(intent);
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

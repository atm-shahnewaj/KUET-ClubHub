package com.kuet.clubhub.ui.events;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.local.AppDatabase;
import com.kuet.clubhub.data.local.ScheduledEventEntity;
import com.kuet.clubhub.databinding.ActivityEventPassBinding;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.Io;
import com.kuet.clubhub.util.QrCodeGenerator;
import com.kuet.clubhub.util.QrPayload;
import com.kuet.clubhub.util.TimeFormats;
import com.kuet.clubhub.util.Years;

import java.util.Date;

/**
 * The student's personal event pass: a QR code the organizer scans at the door.
 * It is drawn from the copy stored on the phone, so it works without internet.
 */
public class EventPassActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";

    private ActivityEventPassBinding binding;
    private UserProfile profile;
    private String eventId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventPassBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        profile = new SessionManager(this).getProfile();
        if (eventId == null || profile == null) {
            finish();
            return;
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // A bright screen makes the code easy to scan.
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);

        binding.tvHolder.setText(profile.getName());
        binding.tvHolderDetails.setText(profile.getStudentId() + "  •  " + profile.getDepartment()
                + "  •  " + Years.label(profile.getYear()));

        loadPass();
    }

    /** Local copy first (works offline); the network only helps older registrations. */
    private void loadPass() {
        final android.content.Context app = getApplicationContext();
        Io.DISK.execute(() -> {
            ScheduledEventEntity row = AppDatabase.getInstance(app).scheduleDao().findById(eventId);
            runOnUiThread(() -> {
                if (row == null) {
                    loadFromServer();
                } else {
                    showEventInfo(row.title, new Date(row.startMillis), new Date(row.endMillis), row.venue);
                    if (!MyRegistration.REGISTERED.equals(row.status)) {
                        showMessage(getString(R.string.pass_not_registered));
                    } else if (row.passCode == null || row.passCode.isEmpty()) {
                        createPassCode();
                    } else {
                        showQr(row.passCode);
                    }
                }
            });
        });
    }

    private void loadFromServer() {
        EventRepository.getInstance(this).getMyRegistration(profile.getUid(), eventId,
                new Callback<MyRegistration>() {
                    @Override
                    public void onSuccess(MyRegistration r) {
                        if (r == null || !MyRegistration.REGISTERED.equals(r.getStatus())) {
                            showMessage(getString(R.string.pass_not_registered));
                            return;
                        }
                        showEventInfo(r.getTitle(), r.getStartTime(), r.getEndTime(), r.getVenue());
                        if (r.getPassCode() == null || r.getPassCode().isEmpty()) {
                            createPassCode();
                        } else {
                            showQr(r.getPassCode());
                        }
                    }

                    @Override
                    public void onError(String message) {
                        showMessage(message);
                    }
                });
    }

    private void createPassCode() {
        EventRepository.getInstance(this).ensurePassCode(eventId, profile.getUid(),
                new Callback<String>() {
                    @Override
                    public void onSuccess(String code) {
                        showQr(code);
                    }

                    @Override
                    public void onError(String message) {
                        showMessage(message);
                    }
                });
    }

    private void showEventInfo(String title, Date start, Date end, String venue) {
        binding.tvEventTitle.setText(title);
        String where = venue == null || venue.isEmpty() ? "" : "  •  " + venue;
        binding.tvEventWhen.setText(TimeFormats.fullDate(start) + "  •  "
                + TimeFormats.timeRange(start, end) + where);
    }

    private void showQr(String passCode) {
        Bitmap qr = QrCodeGenerator.generate(
                QrPayload.forPass(eventId, profile.getUid(), passCode), 720);
        binding.ivQr.setImageBitmap(qr);
        binding.progress.setVisibility(View.GONE);
        binding.tvPassCode.setText(getString(R.string.pass_code_label, passCode));
        binding.tvPassCode.setVisibility(View.VISIBLE);
    }

    private void showMessage(String message) {
        binding.progress.setVisibility(View.GONE);
        binding.tvPassMessage.setText(message);
    }
}

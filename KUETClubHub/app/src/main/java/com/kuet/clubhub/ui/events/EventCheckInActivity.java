package com.kuet.clubhub.ui.events;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.CheckInRepository;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.databinding.ActivityEventCheckinBinding;
import com.kuet.clubhub.model.CheckIn;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.notify.ToastCenter;
import com.kuet.clubhub.ui.scan.ScanActivity;
import com.kuet.clubhub.util.QrCodeGenerator;
import com.kuet.clubhub.util.QrPayload;

import java.util.List;

/**
 * Shows the rotating check-in QR. A fresh token is published to Firestore every 30 seconds and
 * only the newest one is accepted, so a screenshot of the code stops working almost at once.
 */
public class EventCheckInActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_TITLE = "event_title";

    private static final long ROTATE_MS = 30_000L;
    private static final long TICK_MS = 250L;

    private ActivityEventCheckinBinding binding;
    private String eventId;
    private String eventTitle = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean running = false;
    private boolean publishing = false;
    private boolean announcedCheckInOpen = false;
    private long shownAt = 0;          // SystemClock.elapsedRealtime() when the current QR appeared
    private int registered = 0;
    private int checkedIn = 0;

    private ListenerRegistration eventRegistration;
    private ListenerRegistration checkInsRegistration;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            long elapsed = SystemClock.elapsedRealtime() - shownAt;
            if (shownAt != 0) {
                long left = Math.max(0, ROTATE_MS - elapsed);
                binding.countdown.setProgress((int) (left * 1000 / ROTATE_MS));
                binding.tvCountdown.setText(getString(R.string.checkin_refreshing_in,
                        (int) Math.ceil(left / 1000.0)));
            }
            if (!publishing && (shownAt == 0 || elapsed >= ROTATE_MS)) {
                rotate();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventCheckinBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null) {
            finish();
            return;
        }
        String title = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        eventTitle = title == null ? "" : title;
        binding.tvEventTitle.setText(eventTitle);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnScanPasses.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_ORGANIZER_SCAN);
            intent.putExtra(ScanActivity.EXTRA_EVENT_ID, eventId);
            intent.putExtra(ScanActivity.EXTRA_EVENT_TITLE, eventTitle);
            startActivity(intent);
        });

        // The phone must stay awake or the code would stop rotating.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateCounter();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (eventId == null) {
            return;
        }
        eventRegistration = EventRepository.getInstance(this).listenEvent(eventId,
                new EventRepository.EventListener() {
                    @Override
                    public void onEvent(Event event) {
                        registered = (int) event.getRegisteredCount();
                        if (event.getTitle() != null && !event.getTitle().isEmpty()) {
                            eventTitle = event.getTitle();
                            binding.tvEventTitle.setText(eventTitle);
                        }
                        updateCounter();
                    }

                    @Override
                    public void onMissing() {
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });
        checkInsRegistration = CheckInRepository.listenCheckIns(eventId,
                new CheckInRepository.CheckInsListener() {
                    @Override
                    public void onChanged(List<CheckIn> list) {
                        int prev = checkedIn;
                        checkedIn = list.size();
                        if (prev > 0 && checkedIn > prev) {
                            ToastCenter.showShort(EventCheckInActivity.this,
                                    "Attendee checked in (" + checkedIn + "/" + registered + ")");
                        }
                        updateCounter();
                    }

                    @Override
                    public void onError(String message) {
                        showError(message);
                    }
                });

        running = true;
        shownAt = 0;
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        running = false;
        handler.removeCallbacks(ticker);
        if (eventRegistration != null) {
            eventRegistration.remove();
            eventRegistration = null;
        }
        if (checkInsRegistration != null) {
            checkInsRegistration.remove();
            checkInsRegistration = null;
        }
        // Leaving the screen closes check-in: no code is valid any more.
        if (eventId != null) {
            CheckInRepository.closeCheckIn(eventId);
        }
    }

    /** Publishes a new token, and shows its QR only once the server has accepted it. */
    private void rotate() {
        publishing = true;
        final String token = CheckInRepository.newToken();
        CheckInRepository.publishToken(eventId, token, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                publishing = false;
                if (!running) {
                    return;
                }
                Bitmap qr = QrCodeGenerator.generate(QrPayload.forEventToken(eventId, token), 720);
                binding.ivQr.setImageBitmap(qr);
                binding.progress.setVisibility(View.GONE);
                binding.tvError.setVisibility(View.GONE);
                shownAt = SystemClock.elapsedRealtime();
                if (!announcedCheckInOpen) {
                    announcedCheckInOpen = true;
                    ToastCenter.eventStartingOrCheckInOpen(EventCheckInActivity.this,
                            eventTitle, true, true, eventId);
                }
            }

            @Override
            public void onError(String message) {
                publishing = false;
                showError(message);
                // Try again on the next tick; keep showing the old code only if it is still fresh.
                if (shownAt != 0 && SystemClock.elapsedRealtime() - shownAt >= ROTATE_MS) {
                    binding.ivQr.setImageDrawable(null);
                    binding.progress.setVisibility(View.VISIBLE);
                    shownAt = 0;
                }
            }
        });
    }

    private void updateCounter() {
        binding.tvLiveCounter.setText(getString(R.string.checkin_live_counter, checkedIn, registered));
    }

    private void showError(String message) {
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
    }
}

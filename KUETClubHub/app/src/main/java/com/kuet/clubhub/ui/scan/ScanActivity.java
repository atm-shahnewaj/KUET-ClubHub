package com.kuet.clubhub.ui.scan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.mlkit.vision.MlKitAnalyzer;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.interfaces.Detector;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.CheckInRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.TeamRepository;
import com.kuet.clubhub.databinding.ActivityScanBinding;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.util.QrPayload;
import com.kuet.clubhub.util.TimeFormats;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen QR scanner with instant feedback (green / amber / red, sound and vibration).
 *
 * MODE_STUDENT_CHECKIN: a student scans the organizer's rotating code, then the screen closes.
 * MODE_ORGANIZER_SCAN:  an organizer scans student passes one after another.
 * MODE_JOIN_TEAM:       a student scans a team's invite QR code and joins that team.
 * MODE_VERIFY_CERT:     scans a certificate's QR code and returns its id to the caller.
 */
public class ScanActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_TITLE = "event_title";
    public static final String MODE_STUDENT_CHECKIN = "student";
    public static final String MODE_ORGANIZER_SCAN = "organizer";
    public static final String MODE_JOIN_TEAM = "team";
    public static final String MODE_VERIFY_CERT = "cert";
    /** Result extra of MODE_VERIFY_CERT. */
    public static final String EXTRA_CERT_ID = "cert_id";

    private static final int SUCCESS = 0;
    private static final int WARNING = 1;
    private static final int ERROR = 2;

    private static final long ORGANIZER_RESULT_MS = 1500;
    private static final long STUDENT_RESULT_MS = 1800;
    private static final long SAME_CODE_COOLDOWN_MS = 4000;

    private ActivityScanBinding binding;
    private UserProfile profile;
    private String mode;
    private String eventId;
    private String eventTitle = "";

    private BarcodeScanner scanner;
    private ExecutorService analysisExecutor;
    private LifecycleCameraController cameraController;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator tone;

    private boolean handling = false;
    private boolean permissionDeniedOnce = false;
    private String lastRaw;
    private long lastRawAt = 0;
    private int scannedOk = 0;
    private Intent resultData;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    binding.panelPermission.setVisibility(View.GONE);
                    startCamera();
                } else {
                    permissionDeniedOnce = true;
                    binding.panelPermission.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets(binding.getRoot());
        useWhiteStatusBarIcons();

        mode = getIntent().getStringExtra(EXTRA_MODE);
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        String title = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        eventTitle = title == null ? "" : title;
        profile = new SessionManager(this).getProfile();
        // Check-in modes belong to one event; joining a team and verifying a certificate do not.
        boolean needsEvent = MODE_STUDENT_CHECKIN.equals(mode) || MODE_ORGANIZER_SCAN.equals(mode);
        if (mode == null || profile == null || (needsEvent && eventId == null)) {
            finish();
            return;
        }

        boolean organizer = MODE_ORGANIZER_SCAN.equals(mode);
        int titleRes;
        int instructionRes;
        if (MODE_JOIN_TEAM.equals(mode)) {
            titleRes = R.string.title_scan_team;
            instructionRes = R.string.scan_instruction_team;
        } else if (MODE_VERIFY_CERT.equals(mode)) {
            titleRes = R.string.title_scan_certificate;
            instructionRes = R.string.scan_instruction_certificate;
        } else if (organizer) {
            titleRes = R.string.title_scan_passes;
            instructionRes = R.string.scan_instruction_organizer;
        } else {
            titleRes = R.string.title_scan_checkin;
            instructionRes = R.string.scan_instruction_student;
        }
        binding.toolbar.setTitle(titleRes);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.tvInstruction.setText(instructionRes);
        binding.tvCounter.setVisibility(organizer ? View.VISIBLE : View.GONE);
        updateCounter();

        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());
        analysisExecutor = Executors.newSingleThreadExecutor();

        binding.btnPermission.setOnClickListener(v -> {
            if (permissionDeniedOnce) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA);
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from the settings screen with the permission now granted.
        if (cameraController == null && scanner != null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            binding.panelPermission.setVisibility(View.GONE);
            startCamera();
        }
    }

    private void startCamera() {
        if (cameraController != null) {
            return;
        }
        cameraController = new LifecycleCameraController(this);
        cameraController.setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA);
        cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS);

        List<Detector<?>> detectors = new ArrayList<>();
        detectors.add(scanner);
        cameraController.setImageAnalysisAnalyzer(analysisExecutor, new MlKitAnalyzer(
                detectors, ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(this), result -> {
            List<Barcode> barcodes = result == null ? null : result.getValue(scanner);
            if (barcodes == null || barcodes.isEmpty()) {
                return;
            }
            String raw = barcodes.get(0).getRawValue();
            if (raw != null) {
                onCodeScanned(raw);
            }
        }));
        cameraController.bindToLifecycle(this);
        binding.preview.setController(cameraController);
    }

    // ------------------------------------------------------------------ handling a code

    private void onCodeScanned(String raw) {
        if (handling) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (raw.equals(lastRaw) && now - lastRawAt < SAME_CODE_COOLDOWN_MS) {
            return;
        }
        lastRaw = raw;
        lastRawAt = now;
        handling = true;

        QrPayload payload = QrPayload.parse(raw);
        if (MODE_ORGANIZER_SCAN.equals(mode)) {
            handleOrganizerScan(payload);
        } else if (MODE_JOIN_TEAM.equals(mode)) {
            handleTeamScan(payload);
        } else if (MODE_VERIFY_CERT.equals(mode)) {
            handleCertificateScan(payload);
        } else {
            handleStudentScan(payload);
        }
    }

    private void handleTeamScan(@Nullable QrPayload payload) {
        if (payload == null || payload.type != QrPayload.TYPE_TEAM) {
            showResult(ERROR, getString(R.string.scan_not_team_code),
                    getString(R.string.scan_not_team_code_hint), STUDENT_RESULT_MS, false);
            return;
        }
        TeamRepository.joinWithCode(payload.secret, profile, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                showResult(SUCCESS, getString(R.string.scan_joined_team), "",
                        STUDENT_RESULT_MS, true);
            }

            @Override
            public void onError(String message) {
                showResult(ERROR, getString(R.string.scan_join_failed), message,
                        STUDENT_RESULT_MS * 2, false);
            }
        });
    }

    private void handleCertificateScan(@Nullable QrPayload payload) {
        if (payload == null || payload.type != QrPayload.TYPE_CERT) {
            showResult(ERROR, getString(R.string.scan_not_certificate),
                    getString(R.string.scan_not_certificate_hint), STUDENT_RESULT_MS, false);
            return;
        }
        resultData = new Intent().putExtra(EXTRA_CERT_ID, payload.secret);
        showResult(SUCCESS, getString(R.string.scan_certificate_read), payload.secret,
                ORGANIZER_RESULT_MS, true);
    }

    private void handleStudentScan(@Nullable QrPayload payload) {
        if (payload == null || payload.type != QrPayload.TYPE_EVENT_TOKEN) {
            showResult(ERROR, getString(R.string.scan_not_checkin_code),
                    getString(R.string.scan_not_checkin_code_hint), STUDENT_RESULT_MS, false);
            return;
        }
        if (!eventId.equals(payload.eventId)) {
            showResult(ERROR, getString(R.string.scan_wrong_event),
                    getString(R.string.scan_wrong_event_hint), STUDENT_RESULT_MS, false);
            return;
        }
        CheckInRepository.checkInWithToken(eventId, payload.secret, profile.getUid(),
                new Callback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        if ("duplicate".equals(result)) {
                            showResult(WARNING, getString(R.string.scan_already_checked_in),
                                    eventTitle, STUDENT_RESULT_MS, true);
                        } else {
                            showResult(SUCCESS, getString(R.string.scan_checked_in_you),
                                    eventTitle, STUDENT_RESULT_MS, true);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        showResult(ERROR, getString(R.string.scan_failed), message,
                                STUDENT_RESULT_MS * 2, false);
                    }
                });
    }

    private void handleOrganizerScan(@Nullable QrPayload payload) {
        if (payload == null || payload.type != QrPayload.TYPE_PASS) {
            showResult(ERROR, getString(R.string.scan_not_a_pass),
                    getString(R.string.scan_not_a_pass_hint), ORGANIZER_RESULT_MS, false);
            return;
        }
        if (!eventId.equals(payload.eventId)) {
            showResult(ERROR, getString(R.string.scan_wrong_event),
                    getString(R.string.scan_pass_wrong_event_hint), ORGANIZER_RESULT_MS, false);
            return;
        }
        CheckInRepository.checkInWithPass(eventId, payload.uid, payload.secret, profile.getUid(),
                new Callback<CheckInRepository.ScanResult>() {
                    @Override
                    public void onSuccess(CheckInRepository.ScanResult r) {
                        String name = r.name == null ? "" : r.name;
                        switch (r.status) {
                            case CheckInRepository.ScanResult.OK:
                                scannedOk++;
                                updateCounter();
                                showResult(SUCCESS, getString(R.string.scan_checked_in_named),
                                        name, ORGANIZER_RESULT_MS, false);
                                break;
                            case CheckInRepository.ScanResult.DUPLICATE:
                                String when = r.time == null ? ""
                                        : getString(R.string.scan_at_time, TimeFormats.time(r.time));
                                showResult(WARNING, getString(R.string.scan_already_checked_in),
                                        (name + " " + when).trim(), ORGANIZER_RESULT_MS, false);
                                break;
                            case CheckInRepository.ScanResult.WAITLISTED:
                                showResult(ERROR, getString(R.string.scan_waitlisted), name,
                                        ORGANIZER_RESULT_MS, false);
                                break;
                            case CheckInRepository.ScanResult.INVALID_PASS:
                                showResult(ERROR, getString(R.string.scan_invalid_pass), name,
                                        ORGANIZER_RESULT_MS, false);
                                break;
                            default:
                                showResult(ERROR, getString(R.string.scan_not_registered), name,
                                        ORGANIZER_RESULT_MS, false);
                                break;
                        }
                    }

                    @Override
                    public void onError(String message) {
                        showResult(ERROR, getString(R.string.scan_failed), message,
                                ORGANIZER_RESULT_MS * 2, false);
                    }
                });
    }

    private void updateCounter() {
        binding.tvCounter.setText(getString(R.string.scan_session_counter, scannedOk));
    }

    // ------------------------------------------------------------------ instant feedback

    private void showResult(int kind, String title, String subtitle, long durationMs,
                            boolean finishWhenDone) {
        int color;
        int icon;
        switch (kind) {
            case SUCCESS:
                color = Color.parseColor("#00A651");
                icon = R.drawable.ic_check_white;
                break;
            case WARNING:
                color = Color.parseColor("#E65100");
                icon = R.drawable.ic_warning_white;
                break;
            default:
                color = Color.parseColor("#C62828");
                icon = R.drawable.ic_close_white;
                break;
        }
        binding.resultOverlay.setBackgroundColor(color);
        binding.ivResult.setImageResource(icon);
        binding.tvResultTitle.setText(title);
        binding.tvResultSubtitle.setText(subtitle);
        binding.resultOverlay.setVisibility(View.VISIBLE);
        giveFeedback(kind);

        handler.postDelayed(() -> {
            binding.resultOverlay.setVisibility(View.GONE);
            handling = false;
            if (finishWhenDone) {
                setResult(RESULT_OK, resultData);
                finish();
            }
        }, durationMs);
    }

    private void giveFeedback(int kind) {
        try {
            Vibrator vibrator = Build.VERSION.SDK_INT >= 31
                    ? getSystemService(VibratorManager.class).getDefaultVibrator()
                    : getSystemService(Vibrator.class);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (kind == SUCCESS) {
                    vibrator.vibrate(VibrationEffect.createOneShot(120,
                            VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 160, 100, 160}, -1));
                }
            }
        } catch (RuntimeException ignored) {
            // Vibration is a bonus; never let it break scanning.
        }
        try {
            if (tone == null) {
                tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            }
            tone.startTone(kind == SUCCESS ? ToneGenerator.TONE_PROP_ACK
                    : kind == WARNING ? ToneGenerator.TONE_PROP_PROMPT
                    : ToneGenerator.TONE_PROP_NACK, 250);
        } catch (RuntimeException ignored) {
            // Some devices cannot create a ToneGenerator; that is fine.
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (scanner != null) {
            scanner.close();
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
        }
        if (tone != null) {
            tone.release();
        }
        super.onDestroy();
    }
}

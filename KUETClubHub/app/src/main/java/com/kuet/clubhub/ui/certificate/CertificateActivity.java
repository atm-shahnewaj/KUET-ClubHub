package com.kuet.clubhub.ui.certificate;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.CertificateRepository;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityCertificateBinding;
import com.kuet.clubhub.model.Certificate;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.notify.ToastCenter;
import com.kuet.clubhub.util.CertificateRenderer;
import com.kuet.clubhub.util.Io;

import java.io.File;

/**
 * Shows the student's certificate for one event: it is issued on first open (the Firestore rules
 * check attendance and that the event has ended), drawn on the phone, shown as a preview and saved
 * as a PDF that can be opened or shared.
 */
public class CertificateActivity extends BaseActivity {

    public static final String EXTRA_EVENT_ID = "event_id";

    private static final int PREVIEW_WIDTH_PX = 1200;

    private ActivityCertificateBinding binding;
    private UserProfile profile;
    private String eventId;
    private File pdfFile;
    private Bitmap preview;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCertificateBinding.inflate(getLayoutInflater());
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
        binding.btnOpenPdf.setOnClickListener(v -> openPdf());
        binding.btnSharePdf.setOnClickListener(v -> sharePdf());

        loadEvent();
    }

    private void loadEvent() {
        EventRepository.getInstance(this).loadEvent(eventId, new Callback<Event>() {
            @Override
            public void onSuccess(Event event) {
                if (isDestroyed()) {
                    return;
                }
                binding.tvEventTitle.setText(event.getTitle());
                if (!event.hasEnded(System.currentTimeMillis())) {
                    showMessage(getString(R.string.certificate_not_yet));
                    return;
                }
                issue(event);
            }

            @Override
            public void onError(String message) {
                if (!isDestroyed()) {
                    showMessage(message);
                }
            }
        });
    }

    private void issue(Event event) {
        CertificateRepository.getOrIssue(event, profile, new Callback<Certificate>() {
            @Override
            public void onSuccess(Certificate cert) {
                if (!isDestroyed()) {
                    draw(cert);
                }
            }

            @Override
            public void onError(String message) {
                if (!isDestroyed()) {
                    showMessage(message);
                }
            }
        });
    }

    /** Drawing and writing the PDF are slow, so they run on the background thread. */
    private void draw(Certificate cert) {
        final android.content.Context app = getApplicationContext();
        Io.DISK.execute(() -> {
            try {
                CertificateRenderer.Assets assets = CertificateRenderer.loadAssets(app, cert);
                Bitmap bitmap = CertificateRenderer.renderBitmap(cert, assets, PREVIEW_WIDTH_PX);
                File file = CertificateRenderer.writePdf(app, cert, assets);
                assets.recycle();
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    preview = bitmap;
                    pdfFile = file;
                    binding.ivPreview.setImageBitmap(bitmap);
                    binding.progress.setVisibility(View.GONE);
                    binding.tvCertId.setText(getString(R.string.certificate_id_label, cert.getCertId()));
                    binding.tvCertId.setVisibility(View.VISIBLE);
                    binding.btnOpenPdf.setVisibility(View.VISIBLE);
                    binding.btnSharePdf.setVisibility(View.VISIBLE);
                    ToastCenter.certificateReady(CertificateActivity.this,
                            cert.getEventTitle(), eventId);
                });
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        showMessage(getString(R.string.certificate_draw_failed));
                    }
                });
            }
        });
    }

    private void showMessage(String message) {
        binding.progress.setVisibility(View.GONE);
        binding.tvMessage.setText(message);
        binding.tvMessage.setVisibility(View.VISIBLE);
    }

    private Uri pdfUri() {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
    }

    /** Implicit Intent: any installed PDF viewer can open the certificate. */
    private void openPdf() {
        if (pdfFile == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(pdfUri(), "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.msg_no_pdf_viewer, Toast.LENGTH_LONG).show();
        }
    }

    /** Implicit Intent: send the PDF to chat apps, email or Drive (this is also how to save it). */
    private void sharePdf() {
        if (pdfFile == null) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/pdf");
        send.putExtra(Intent.EXTRA_STREAM, pdfUri());
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.action_share_pdf)));
    }

    @Override
    protected void onDestroy() {
        binding.ivPreview.setImageDrawable(null);
        preview = null;
        super.onDestroy();
    }
}

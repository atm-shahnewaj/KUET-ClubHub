package com.kuet.clubhub.ui.certificate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.CertificateRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.ActivityVerifyCertificateBinding;
import com.kuet.clubhub.model.Certificate;
import com.kuet.clubhub.ui.scan.ScanActivity;
import com.kuet.clubhub.util.TimeFormats;

/**
 * Anyone signed in can check a certificate: type the id printed on it, or scan its QR code.
 * The answer comes from the certificate record stored in Firestore.
 */
public class VerifyCertificateActivity extends BaseActivity {

    private ActivityVerifyCertificateBinding binding;
    private int requestCounter = 0;

    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String id = result.getData().getStringExtra(ScanActivity.EXTRA_CERT_ID);
                    if (id != null) {
                        binding.etCertId.setText(id);
                        verify();
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVerifyCertificateBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        if (new SessionManager(this).getProfile() == null) {
            finish();
            return;
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnVerify.setOnClickListener(v -> verify());
        binding.etCertId.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                verify();
                return true;
            }
            return false;
        });
        binding.btnScanCert.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_VERIFY_CERT);
            scanLauncher.launch(intent);
        });
    }

    private void verify() {
        String id = CertificateRepository.normalizeId(
                binding.etCertId.getText() == null ? "" : binding.etCertId.getText().toString());
        if (id.isEmpty()) {
            binding.tilCertId.setError(getString(R.string.error_certificate_id_required));
            return;
        }
        binding.tilCertId.setError(null);
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.etCertId.getWindowToken(), 0);
        }

        final int myRequest = ++requestCounter;
        binding.progress.setVisibility(View.VISIBLE);
        binding.tvResultStatus.setVisibility(View.GONE);
        binding.cardResult.setVisibility(View.GONE);
        binding.btnVerify.setEnabled(false);

        CertificateRepository.verify(id, new Callback<Certificate>() {
            @Override
            public void onSuccess(Certificate cert) {
                if (isDestroyed() || myRequest != requestCounter) {
                    return;
                }
                finishRequest();
                if (cert == null) {
                    showStatus(getString(R.string.verify_not_found), true);
                } else {
                    showCertificate(cert);
                }
            }

            @Override
            public void onError(String message) {
                if (isDestroyed() || myRequest != requestCounter) {
                    return;
                }
                finishRequest();
                showStatus(message, true);
            }
        });
    }

    private void finishRequest() {
        binding.progress.setVisibility(View.GONE);
        binding.btnVerify.setEnabled(true);
    }

    private void showStatus(String text, boolean problem) {
        binding.tvResultStatus.setText(text);
        binding.tvResultStatus.setBackgroundResource(problem
                ? R.drawable.bg_pill_alert : R.drawable.bg_pill);
        binding.tvResultStatus.setVisibility(View.VISIBLE);
    }

    private void showCertificate(Certificate cert) {
        showStatus(getString(R.string.verify_valid), false);
        binding.tvResultName.setText(cert.getName());
        binding.tvResultStudent.setText(cert.getStudentId() == null ? ""
                : getString(R.string.student_id_line, cert.getStudentId()));
        binding.tvResultEvent.setText(cert.getEventTitle());
        binding.tvResultClub.setText(cert.getClubName());
        binding.tvResultDates.setText(getString(R.string.verify_dates,
                TimeFormats.fullDate(cert.getEventDate()),
                TimeFormats.fullDate(cert.getIssuedAt()), cert.getCertId()));
        binding.cardResult.setVisibility(View.VISIBLE);
    }
}

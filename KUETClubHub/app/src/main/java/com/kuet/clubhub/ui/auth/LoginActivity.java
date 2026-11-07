package com.kuet.clubhub.ui.auth;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.UserRepository;
import com.kuet.clubhub.databinding.ActivityLoginBinding;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.MainActivity;
import com.kuet.clubhub.util.SigningCertificate;

/**
 * Gmail-only authentication screen:
 * - Primary: One-tap "Continue with Google (Gmail)" via Firebase Auth Google OAuth Provider
 * - Secondary: Gmail (@gmail.com) address + password sign-in
 */
public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        // The green hero runs under the status bar, so the icons stay light in both themes.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        binding.btnGoogleLogin.setOnClickListener(v -> attemptGoogleLogin());
        binding.btnLogin.setOnClickListener(v -> attemptGmailLogin());
        binding.btnForgot.setOnClickListener(v -> sendReset());
        binding.btnCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptGoogleLogin() {
        setLoading(true);
        UserRepository.signInWithGoogle(this, new Callback<FirebaseUser>() {
            @Override
            public void onSuccess(FirebaseUser user) {
                UserRepository.fetchProfile(user.getUid(), new Callback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile profile) {
                        new SessionManager(LoginActivity.this).save(profile);
                        startClearTask(MainActivity.class);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        if (UserRepository.ERR_NO_PROFILE.equals(message)) {
                            Intent setup = new Intent(LoginActivity.this, RegisterActivity.class);
                            setup.putExtra(RegisterActivity.EXTRA_GOOGLE_MODE, true);
                            startActivity(setup);
                        } else {
                            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                if (UserRepository.ERR_CERT_HASH.equals(message)) {
                    showCertificateHelp();
                    return;
                }
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Firebase only accepts Google sign-in from builds whose signing key is registered in the
     * console. Show this build's fingerprints so they can be copied straight into Firebase.
     */
    private void showCertificateHelp() {
        final String sha1 = SigningCertificate.fingerprint(this, "SHA-1");
        final String sha256 = SigningCertificate.fingerprint(this, "SHA-256");
        String message = getString(R.string.cert_help_message,
                sha1 == null ? "-" : sha1, sha256 == null ? "-" : sha256);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cert_help_title)
                .setMessage(message)
                .setPositiveButton(R.string.cert_help_copy_sha1, (d, w) -> copyToClipboard("SHA-1", sha1))
                .setNeutralButton(R.string.cert_help_copy_sha256, (d, w) -> copyToClipboard("SHA-256", sha256))
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private void copyToClipboard(String label, @Nullable String value) {
        if (value == null) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, getString(R.string.cert_help_copied, label), Toast.LENGTH_SHORT).show();
        }
    }

    private void attemptGmailLogin() {
        String email = textOf(binding.etEmail);
        String password = binding.etPassword.getText() == null
                ? "" : binding.etPassword.getText().toString();

        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        boolean valid = true;
        if (!UserRepository.isValidGmail(email)) {
            binding.tilEmail.setError(getString(R.string.error_email_invalid));
            valid = false;
        }
        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.error_password_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        UserRepository.login(email, password, new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                new SessionManager(LoginActivity.this).save(result);
                startClearTask(MainActivity.class);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                if (UserRepository.ERR_NO_PROFILE.equals(message)) {
                    Intent setup = new Intent(LoginActivity.this, RegisterActivity.class);
                    setup.putExtra(RegisterActivity.EXTRA_GOOGLE_MODE, true);
                    startActivity(setup);
                } else {
                    Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void sendReset() {
        String email = textOf(binding.etEmail);
        binding.tilEmail.setError(null);
        if (!UserRepository.isValidGmail(email)) {
            binding.tilEmail.setError(getString(R.string.error_email_invalid));
            return;
        }
        setLoading(true);
        UserRepository.sendPasswordReset(email, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
                Snackbar.make(binding.getRoot(), R.string.msg_reset_sent,
                        Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnGoogleLogin.setEnabled(!loading);
        binding.btnLogin.setEnabled(!loading);
        binding.btnForgot.setEnabled(!loading);
        binding.btnCreateAccount.setEnabled(!loading);
    }

    private static String textOf(com.google.android.material.textfield.TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}

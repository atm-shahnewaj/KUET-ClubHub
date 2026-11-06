package com.kuet.clubhub.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

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
        useWhiteStatusBarIcons();

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
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
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

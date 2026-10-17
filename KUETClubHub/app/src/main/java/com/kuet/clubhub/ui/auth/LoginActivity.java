package com.kuet.clubhub.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;

import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.UserRepository;
import com.kuet.clubhub.databinding.ActivityLoginBinding;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.MainActivity;

public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnForgot.setOnClickListener(v -> sendReset());
        binding.btnCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String email = textOf(binding.etEmail);
        String password = binding.etPassword.getText() == null
                ? "" : binding.etPassword.getText().toString();

        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        boolean valid = true;
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void sendReset() {
        String email = textOf(binding.etEmail);
        binding.tilEmail.setError(null);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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
        binding.progress.setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.btnLogin.setEnabled(!loading);
        binding.btnForgot.setEnabled(!loading);
        binding.btnCreateAccount.setEnabled(!loading);
    }

    private static String textOf(com.google.android.material.textfield.TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}

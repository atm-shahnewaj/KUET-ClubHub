package com.kuet.clubhub.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.UserRepository;
import com.kuet.clubhub.databinding.ActivityRegisterBinding;
import com.kuet.clubhub.model.Role;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.MainActivity;
import com.kuet.clubhub.util.PickerUtil;
import com.kuet.clubhub.util.Years;

public class RegisterActivity extends BaseActivity {

    private ActivityRegisterBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsWithHeader(binding.getRoot(), binding.header);
        useWhiteStatusBarIcons();

        View.OnClickListener pickYear = v -> PickerUtil.showChoice(
                this,
                getString(R.string.title_pick_year),
                Years.LABELS,
                textOf(binding.etYear),
                picked -> binding.etYear.setText(picked));
        binding.etYear.setOnClickListener(pickYear);
        binding.tilYear.setEndIconOnClickListener(pickYear);

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.btnHaveAccount.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String name = textOf(binding.etName);
        String studentId = textOf(binding.etStudentId);
        String department = textOf(binding.etDepartment);
        int year = Years.fromLabel(textOf(binding.etYear));
        String email = textOf(binding.etEmail);
        String password = rawTextOf(binding.etPassword);
        String confirm = rawTextOf(binding.etConfirmPassword);
        Role role = binding.toggleRole.getCheckedButtonId() == R.id.btn_role_organizer
                ? Role.ORGANIZER : Role.STUDENT;

        binding.tilName.setError(null);
        binding.tilStudentId.setError(null);
        binding.tilDepartment.setError(null);
        binding.tilYear.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);

        boolean valid = true;
        if (name.length() < 2) {
            binding.tilName.setError(getString(R.string.error_name_required));
            valid = false;
        }
        if (studentId.isEmpty()) {
            binding.tilStudentId.setError(getString(R.string.error_student_id_required));
            valid = false;
        }
        if (department.isEmpty()) {
            binding.tilDepartment.setError(getString(R.string.error_department_required));
            valid = false;
        }
        if (year == 0) {
            binding.tilYear.setError(getString(R.string.error_year_required));
            valid = false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_email_invalid));
            valid = false;
        }
        if (password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.error_password_short));
            valid = false;
        }
        if (!password.equals(confirm)) {
            binding.tilConfirmPassword.setError(getString(R.string.error_password_mismatch));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        UserRepository.register(name, studentId, department, year, email, password, role,
                new Callback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile result) {
                        new SessionManager(RegisterActivity.this).save(result);
                        startClearTask(MainActivity.class);
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
        binding.btnRegister.setEnabled(!loading);
        binding.btnHaveAccount.setEnabled(!loading);
    }

    private static String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private static String rawTextOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }
}

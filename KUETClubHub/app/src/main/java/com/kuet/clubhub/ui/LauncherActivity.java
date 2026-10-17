package com.kuet.clubhub.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.kuet.clubhub.R;
import com.kuet.clubhub.base.BaseActivity;
import com.kuet.clubhub.data.Callback;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.UserRepository;
import com.kuet.clubhub.databinding.ActivityLauncherBinding;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.auth.LoginActivity;

/** Entry point: decides between Login and Main depending on the saved session. */
public class LauncherActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityLauncherBinding binding = ActivityLauncherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets(binding.getRoot());
        route();
    }

    private void route() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startClearTask(LoginActivity.class);
            return;
        }

        SessionManager session = new SessionManager(this);
        if (session.getCachedProfile(user.getUid()) != null) {
            startClearTask(MainActivity.class);
            return;
        }

        // Signed in, but this device has no saved profile yet: fetch it first.
        UserRepository.fetchProfile(user.getUid(), new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                session.save(result);
                startClearTask(MainActivity.class);
            }

            @Override
            public void onError(String message) {
                FirebaseAuth.getInstance().signOut();
                Toast.makeText(LauncherActivity.this,
                        R.string.msg_profile_load_failed, Toast.LENGTH_LONG).show();
                startClearTask(LoginActivity.class);
            }
        });
    }
}

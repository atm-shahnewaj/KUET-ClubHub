package com.kuet.clubhub.ui;

import android.content.Intent;
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
import com.kuet.clubhub.ui.auth.RegisterActivity;
import com.kuet.clubhub.ui.events.EventDetailActivity;

/** Entry point: decides between Login, Google Profile Completion, and Main depending on the saved session. */
public class LauncherActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityLauncherBinding binding = ActivityLauncherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets(binding.getRoot());
        useWhiteStatusBarIcons();
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
            openMain();
            return;
        }

        // Signed in, but this device has no saved profile yet: fetch it first.
        UserRepository.fetchProfile(user.getUid(), new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                session.save(result);
                openMain();
            }

            @Override
            public void onError(String message) {
                if (UserRepository.ERR_NO_PROFILE.equals(message)) {
                    Intent setup = new Intent(LauncherActivity.this, RegisterActivity.class);
                    setup.putExtra(RegisterActivity.EXTRA_GOOGLE_MODE, true);
                    setup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(setup);
                    finish();
                    return;
                }
                FirebaseAuth.getInstance().signOut();
                Toast.makeText(LauncherActivity.this,
                        R.string.msg_profile_load_failed, Toast.LENGTH_LONG).show();
                startClearTask(LoginActivity.class);
            }
        });
    }

    /** Forwards any launcher shortcut or home-screen widget action to MainActivity. */
    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Intent incoming = getIntent();
        if (incoming != null) {
            if (incoming.getAction() != null) {
                intent.setAction(incoming.getAction());
            }
            String eventId = incoming.getStringExtra(EventDetailActivity.EXTRA_EVENT_ID);
            if (eventId != null) {
                intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, eventId);
            }
        }
        startActivity(intent);
        finish();
    }
}

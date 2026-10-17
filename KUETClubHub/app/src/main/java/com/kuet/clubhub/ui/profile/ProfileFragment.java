package com.kuet.clubhub.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.data.UserRepository;
import com.kuet.clubhub.databinding.FragmentProfileBinding;
import com.kuet.clubhub.model.UserProfile;
import com.kuet.clubhub.ui.auth.LoginActivity;
import com.kuet.clubhub.util.Years;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserProfile p = new SessionManager(requireContext()).getProfile();
        if (p != null) {
            String name = p.getName() == null ? "" : p.getName();
            binding.tvAvatar.setText(name.isEmpty() ? "?"
                    : name.substring(0, 1).toUpperCase(Locale.ROOT));
            binding.tvName.setText(name);
            binding.tvRole.setText(p.getRoleEnum().getLabel());
            binding.tvEmail.setText(p.getEmail());
            binding.tvStudentId.setText(p.getStudentId());
            binding.tvDepartment.setText(p.getDepartment());
            binding.tvYear.setText(Years.label(p.getYear()));
        }

        binding.btnLogout.setOnClickListener(v -> {
            UserRepository.logout(requireContext());
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

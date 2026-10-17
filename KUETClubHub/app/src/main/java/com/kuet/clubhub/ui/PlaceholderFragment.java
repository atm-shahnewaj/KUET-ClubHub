package com.kuet.clubhub.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kuet.clubhub.databinding.FragmentPlaceholderBinding;

/** Simple "coming soon" screen for tabs that arrive in later weeks. */
public class PlaceholderFragment extends Fragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";

    public static PlaceholderFragment newInstance(String title, String message) {
        PlaceholderFragment f = new PlaceholderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FragmentPlaceholderBinding binding =
                FragmentPlaceholderBinding.inflate(inflater, container, false);
        Bundle args = requireArguments();
        binding.tvTitle.setText(args.getString(ARG_TITLE));
        binding.tvMessage.setText(args.getString(ARG_MESSAGE));
        return binding.getRoot();
    }
}

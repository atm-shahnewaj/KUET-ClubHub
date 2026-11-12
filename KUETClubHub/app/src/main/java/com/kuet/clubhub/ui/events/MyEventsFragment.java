package com.kuet.clubhub.ui.events;

import androidx.recyclerview.widget.GridLayoutManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.clubhub.R;
import com.kuet.clubhub.data.EventRepository;
import com.kuet.clubhub.data.SessionManager;
import com.kuet.clubhub.databinding.FragmentMyEventsBinding;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.UserProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The events the signed-in person is registered for or waiting on. */
public class MyEventsFragment extends Fragment {

    private FragmentMyEventsBinding binding;
    private EventAdapter adapter;
    private UserProfile profile;
    private ListenerRegistration registration;
    private boolean loaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMyEventsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        profile = new SessionManager(requireContext()).getProfile();
        adapter = new EventAdapter(false, event -> {
            Intent intent = new Intent(requireContext(), EventDetailActivity.class);
            intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.getId());
            startActivity(intent);
        });
        binding.recycler.setLayoutManager(new GridLayoutManager(requireContext(),
                getResources().getInteger(R.integer.list_columns)));
        binding.recycler.setAdapter(adapter);
        binding.progress.setVisibility(View.VISIBLE);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (profile == null) {
            return;
        }
        registration = EventRepository.getInstance(requireContext())
                .listenMyRegistrations(profile.getUid(), new EventRepository.MyRegistrationsListener() {
                    @Override
                    public void onChanged(List<MyRegistration> registrations) {
                        show(registrations);
                    }

                    @Override
                    public void onError(String message) {
                        loaded = true;
                        if (binding != null) {
                            binding.progress.setVisibility(View.GONE);
                            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
                        }
                    }
                });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void show(List<MyRegistration> registrations) {
        if (binding == null) {
            return;
        }
        loaded = true;
        List<MyRegistration> active = new ArrayList<>();
        for (MyRegistration r : registrations) {
            if (r.isActive()) {
                active.add(r);
            }
        }
        Collections.sort(active, (a, b) -> Long.compare(millis(a.getStartTime()), millis(b.getStartTime())));

        List<Event> events = new ArrayList<>();
        Map<String, String> status = new HashMap<>();
        for (MyRegistration r : active) {
            events.add(r.toEvent());
            status.put(r.getEventId(), r.getStatus());
        }
        adapter.submitList(events);
        adapter.setMyStatus(status);

        binding.progress.setVisibility(View.GONE);
        binding.emptyContainer.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
        binding.emptyState.setText(R.string.empty_my_events);
    }

    private static long millis(Date d) {
        return d == null ? 0 : d.getTime();
    }
}

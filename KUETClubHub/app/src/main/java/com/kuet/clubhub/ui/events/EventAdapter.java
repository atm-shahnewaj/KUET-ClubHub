package com.kuet.clubhub.ui.events;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.clubhub.R;
import com.kuet.clubhub.databinding.ItemEventBinding;
import com.kuet.clubhub.model.Event;
import com.kuet.clubhub.model.EventType;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.util.EventTypeStyle;
import com.kuet.clubhub.util.TimeFormats;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EventAdapter extends ListAdapter<Event, EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    private static final DiffUtil.ItemCallback<Event> DIFF = new DiffUtil.ItemCallback<Event>() {
        @Override
        public boolean areItemsTheSame(@NonNull Event a, @NonNull Event b) {
            return a.getId() != null && a.getId().equals(b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Event a, @NonNull Event b) {
            return Objects.equals(a.getTitle(), b.getTitle())
                    && Objects.equals(a.getClubName(), b.getClubName())
                    && Objects.equals(a.getType(), b.getType())
                    && Objects.equals(a.getVenue(), b.getVenue())
                    && Objects.equals(a.getStartTime(), b.getStartTime())
                    && Objects.equals(a.getEndTime(), b.getEndTime())
                    && a.getCapacity() == b.getCapacity()
                    && a.getRegisteredCount() == b.getRegisteredCount()
                    && a.getWaitlistCount() == b.getWaitlistCount();
        }
    };

    private final OnEventClickListener listener;
    /** eventId -> "registered" / "waitlisted" for the signed-in person. */
    private Map<String, String> myStatus = new HashMap<>();
    /** When true, seat information is hidden (used by the "My events" list). */
    private final boolean showSeats;

    public EventAdapter(boolean showSeats, OnEventClickListener listener) {
        super(DIFF);
        this.showSeats = showSeats;
        this.listener = listener;
    }

    public void setMyStatus(Map<String, String> status) {
        this.myStatus = status == null ? new HashMap<>() : status;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new EventViewHolder(
                ItemEventBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = getItem(position);
        holder.bind(event, myStatus.get(event.getId()), showSeats, listener);
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {

        private final ItemEventBinding binding;

        EventViewHolder(ItemEventBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Event e, String myStatus, boolean showSeats, OnEventClickListener listener) {
            Context ctx = itemView.getContext();
            EventType type = e.typeEnum();

            binding.tvDay.setText(TimeFormats.dayNumber(e.getStartTime()));
            binding.tvMonth.setText(TimeFormats.monthShort(e.getStartTime()).toUpperCase());
            binding.tvTitle.setText(e.getTitle());

            String club = e.getClubName() == null ? "" : e.getClubName();
            String when = TimeFormats.timeRange(e.getStartTime(), e.getEndTime());
            binding.tvMeta.setText(club.isEmpty() ? when : club + "  •  " + when);
            binding.tvVenue.setText(e.getVenue());

            EventTypeStyle.apply(binding.tvType, type);

            long now = System.currentTimeMillis();
            if (!showSeats) {
                binding.tvSeats.setVisibility(View.GONE);
            } else {
                binding.tvSeats.setVisibility(View.VISIBLE);
                if (e.hasEnded(now)) {
                    binding.tvSeats.setText(R.string.event_ended);
                } else if (e.getCapacity() > 0 && e.seatsLeft() == 0) {
                    binding.tvSeats.setText(ctx.getString(R.string.seats_full_waitlist,
                            (int) e.getWaitlistCount()));
                } else {
                    binding.tvSeats.setText(ctx.getString(R.string.seats_left,
                            (int) e.seatsLeft(), (int) e.getCapacity()));
                }
            }

            if (MyRegistration.REGISTERED.equals(myStatus)) {
                binding.tvStatus.setVisibility(View.VISIBLE);
                binding.tvStatus.setText(R.string.status_registered);
            } else if (MyRegistration.WAITLISTED.equals(myStatus)) {
                binding.tvStatus.setVisibility(View.VISIBLE);
                binding.tvStatus.setText(R.string.status_waitlisted);
            } else {
                binding.tvStatus.setVisibility(View.GONE);
            }

            binding.getRoot().setOnClickListener(v -> listener.onEventClick(e));
        }
    }
}

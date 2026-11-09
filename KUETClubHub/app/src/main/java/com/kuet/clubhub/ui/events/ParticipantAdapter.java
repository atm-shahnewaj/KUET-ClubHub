package com.kuet.clubhub.ui.events;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.clubhub.R;
import com.kuet.clubhub.databinding.ItemParticipantBinding;
import com.kuet.clubhub.model.MyRegistration;
import com.kuet.clubhub.model.Participant;
import com.kuet.clubhub.util.TimeFormats;

import java.util.Locale;
import java.util.Objects;

/** Rows of the organizer dashboard: who is registered, waiting, and already checked in. */
public class ParticipantAdapter
        extends ListAdapter<Participant, ParticipantAdapter.ParticipantViewHolder> {

    public interface OnParticipantClickListener {
        void onParticipantClick(Participant participant);
    }

    private static final DiffUtil.ItemCallback<Participant> DIFF =
            new DiffUtil.ItemCallback<Participant>() {
                @Override
                public boolean areItemsTheSame(@NonNull Participant a, @NonNull Participant b) {
                    return a.getUid() != null && a.getUid().equals(b.getUid());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Participant a, @NonNull Participant b) {
                    return Objects.equals(a.getName(), b.getName())
                            && Objects.equals(a.getStatus(), b.getStatus())
                            && a.getWaitSeq() == b.getWaitSeq()
                            && a.isCheckedIn() == b.isCheckedIn();
                }
            };

    private final OnParticipantClickListener listener;

    public ParticipantAdapter(OnParticipantClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ParticipantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ParticipantViewHolder(ItemParticipantBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ParticipantViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    public static class ParticipantViewHolder extends RecyclerView.ViewHolder {

        private final ItemParticipantBinding binding;

        ParticipantViewHolder(ItemParticipantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Participant p, OnParticipantClickListener listener) {
            String name = p.getName() == null ? "" : p.getName();
            binding.tvAvatar.setText(name.isEmpty() ? "?"
                    : name.substring(0, 1).toUpperCase(Locale.ROOT));
            com.kuet.clubhub.util.UserPhotos.bind(binding.ivAvatarPhoto, binding.tvAvatar,
                    p.getUid(), name);
            binding.tvName.setText(name);

            String details = p.getStudentId() + "  •  " + p.getDepartment();
            boolean waiting = MyRegistration.WAITLISTED.equals(p.getStatus());
            if (p.isCheckedIn() && p.getCheckedInAt() != null) {
                details += "  •  " + TimeFormats.time(p.getCheckedInAt());
            }
            binding.tvDetails.setText(details);

            if (waiting) {
                binding.tvStatus.setText(R.string.status_waitlisted);
                binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_alert);
            } else if (p.isCheckedIn()) {
                binding.tvStatus.setText(R.string.status_checked_in);
                binding.tvStatus.setBackgroundResource(R.drawable.bg_pill);
            } else {
                binding.tvStatus.setText(R.string.status_not_yet);
                binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_neutral);
            }
            binding.getRoot().setOnClickListener(v -> listener.onParticipantClick(p));
        }
    }
}

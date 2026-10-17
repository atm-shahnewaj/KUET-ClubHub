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
import com.kuet.clubhub.util.Years;

import java.util.Locale;
import java.util.Objects;

public class ParticipantAdapter
        extends ListAdapter<Participant, ParticipantAdapter.ParticipantViewHolder> {

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
                            && a.getWaitSeq() == b.getWaitSeq();
                }
            };

    public ParticipantAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public ParticipantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ParticipantViewHolder(ItemParticipantBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ParticipantViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ParticipantViewHolder extends RecyclerView.ViewHolder {

        private final ItemParticipantBinding binding;

        ParticipantViewHolder(ItemParticipantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Participant p) {
            String name = p.getName() == null ? "" : p.getName();
            binding.tvAvatar.setText(name.isEmpty() ? "?"
                    : name.substring(0, 1).toUpperCase(Locale.ROOT));
            binding.tvName.setText(name);
            binding.tvDetails.setText(p.getStudentId() + "  •  " + p.getDepartment());
            boolean waiting = MyRegistration.WAITLISTED.equals(p.getStatus());
            binding.tvStatus.setText(waiting ? R.string.status_waitlisted : R.string.status_registered);
            binding.tvStatus.setBackgroundResource(waiting
                    ? R.drawable.bg_pill_alert : R.drawable.bg_pill);
        }
    }
}

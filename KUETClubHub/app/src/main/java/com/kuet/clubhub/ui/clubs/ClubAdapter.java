package com.kuet.clubhub.ui.clubs;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.clubhub.R;
import com.kuet.clubhub.databinding.ItemClubBinding;
import com.kuet.clubhub.model.Club;

import java.util.Locale;
import java.util.Objects;

public class ClubAdapter extends ListAdapter<Club, ClubAdapter.ClubViewHolder> {

    public interface OnClubClickListener {
        void onClubClick(Club club);
    }

    private static final DiffUtil.ItemCallback<Club> DIFF = new DiffUtil.ItemCallback<Club>() {
        @Override
        public boolean areItemsTheSame(@NonNull Club a, @NonNull Club b) {
            return a.getId() != null && a.getId().equals(b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Club a, @NonNull Club b) {
            return Objects.equals(a.getName(), b.getName())
                    && Objects.equals(a.getCategory(), b.getCategory())
                    && Objects.equals(a.getDescription(), b.getDescription())
                    && a.getMemberCount() == b.getMemberCount()
                    && a.isApproved() == b.isApproved();
        }
    };

    private final OnClubClickListener listener;

    public ClubAdapter(OnClubClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ClubViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemClubBinding binding =
                ItemClubBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ClubViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ClubViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    public static class ClubViewHolder extends RecyclerView.ViewHolder {

        private final ItemClubBinding binding;

        ClubViewHolder(ItemClubBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Club club, OnClubClickListener listener) {
            String name = club.getName() == null ? "" : club.getName();
            binding.tvAvatar.setText(name.isEmpty() ? "?"
                    : name.substring(0, 1).toUpperCase(Locale.ROOT));
            binding.tvName.setText(name);
            binding.tvCategory.setText(club.getCategory());
            binding.tvDescription.setText(club.getDescription());
            int members = (int) club.getMemberCount();
            binding.tvMembers.setText(itemView.getResources()
                    .getQuantityString(R.plurals.member_count, members, members));
            binding.tvPending.setVisibility(club.isApproved() ? View.GONE : View.VISIBLE);
            binding.getRoot().setOnClickListener(v -> listener.onClubClick(club));
        }
    }
}

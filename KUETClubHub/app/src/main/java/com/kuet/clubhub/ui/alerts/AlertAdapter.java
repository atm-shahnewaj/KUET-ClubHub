package com.kuet.clubhub.ui.alerts;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.clubhub.data.local.AlertEntity;
import com.kuet.clubhub.databinding.ItemAlertBinding;

import java.util.Objects;

public class AlertAdapter extends ListAdapter<AlertEntity, AlertAdapter.AlertViewHolder> {

    public interface OnAlertClickListener {
        void onAlertClick(AlertEntity alert);
    }

    private static final DiffUtil.ItemCallback<AlertEntity> DIFF =
            new DiffUtil.ItemCallback<AlertEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull AlertEntity a, @NonNull AlertEntity b) {
                    return a.eventId.equals(b.eventId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull AlertEntity a, @NonNull AlertEntity b) {
                    return Objects.equals(a.title, b.title)
                            && Objects.equals(a.message, b.message)
                            && a.seen == b.seen;
                }
            };

    private final OnAlertClickListener listener;

    public AlertAdapter(OnAlertClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AlertViewHolder(ItemAlertBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    public static class AlertViewHolder extends RecyclerView.ViewHolder {

        private final ItemAlertBinding binding;

        AlertViewHolder(ItemAlertBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AlertEntity alert, OnAlertClickListener listener) {
            binding.tvTitle.setText(alert.title);
            binding.tvMessage.setText(alert.message);
            binding.tvTime.setText(DateUtils.getRelativeTimeSpanString(alert.createdMillis,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            binding.dotUnread.setVisibility(alert.seen ? View.INVISIBLE : View.VISIBLE);
            binding.getRoot().setOnClickListener(v -> listener.onAlertClick(alert));
        }
    }
}

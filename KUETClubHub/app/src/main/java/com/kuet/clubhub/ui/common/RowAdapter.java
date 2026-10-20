package com.kuet.clubhub.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.clubhub.R;
import com.kuet.clubhub.databinding.ItemRowBinding;
import com.kuet.clubhub.databinding.ItemRowHeaderBinding;

import java.util.Locale;
import java.util.Objects;

/**
 * One adapter for the simple list screens (team boards, member lists, feedback): section headers
 * and rows with an avatar letter, title, subtitle, an optional pill and up to two action buttons.
 */
public class RowAdapter extends ListAdapter<RowAdapter.Row, RecyclerView.ViewHolder> {

    public static final int PILL_NONE = 0;
    public static final int PILL_GREEN = 1;
    public static final int PILL_NEUTRAL = 2;
    public static final int PILL_ALERT = 3;

    private static final int VIEW_HEADER = 0;
    private static final int VIEW_ITEM = 1;

    /** What one row shows. Built with the fluent setters. */
    public static final class Row {
        final boolean header;
        public final String id;
        String title = "";
        String subtitle = "";
        String avatar = "";
        String pill = "";
        int pillStyle = PILL_NONE;
        String primary = "";
        String secondary = "";
        boolean clickable = false;
        /** Whatever the screen wants back when the row is tapped. */
        public Object tag;

        private Row(boolean header, String id) {
            this.header = header;
            this.id = id;
        }

        public static Row header(String title) {
            Row r = new Row(true, "header:" + title);
            r.title = title;
            return r;
        }

        public static Row item(String id) {
            return new Row(false, "item:" + id);
        }

        public Row title(String v) { title = nz(v); return this; }
        public Row subtitle(String v) { subtitle = nz(v); return this; }
        public Row avatar(String v) { avatar = nz(v); return this; }
        public Row pill(String text, int style) { pill = nz(text); pillStyle = style; return this; }
        public Row primary(String label) { primary = nz(label); return this; }
        public Row secondary(String label) { secondary = nz(label); return this; }
        public Row clickable(boolean v) { clickable = v; return this; }
        public Row tag(Object v) { tag = v; return this; }

        /** First letter of a name, upper-case, for the round avatar. */
        public static String initial(@Nullable String name) {
            if (name == null || name.trim().isEmpty()) {
                return "?";
            }
            return name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        }

        private static String nz(String s) {
            return s == null ? "" : s;
        }
    }

    public interface Listener {
        void onRowClick(Row row);

        void onPrimary(Row row);

        void onSecondary(Row row);
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.id.equals(b.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            return Objects.equals(a.title, b.title)
                    && Objects.equals(a.subtitle, b.subtitle)
                    && Objects.equals(a.avatar, b.avatar)
                    && Objects.equals(a.pill, b.pill)
                    && a.pillStyle == b.pillStyle
                    && Objects.equals(a.primary, b.primary)
                    && Objects.equals(a.secondary, b.secondary)
                    && a.clickable == b.clickable;
        }
    };

    private final Listener listener;

    public RowAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).header ? VIEW_HEADER : VIEW_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_HEADER) {
            return new HeaderHolder(ItemRowHeaderBinding.inflate(inflater, parent, false));
        }
        return new ItemHolder(ItemRowBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).binding.tvHeader.setText(row.title);
        } else {
            ((ItemHolder) holder).bind(row, listener);
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemRowHeaderBinding binding;

        HeaderHolder(ItemRowHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final class ItemHolder extends RecyclerView.ViewHolder {
        final ItemRowBinding binding;

        ItemHolder(ItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row, Listener listener) {
            binding.tvAvatar.setText(row.avatar);
            binding.tvTitle.setText(row.title);
            binding.tvSubtitle.setText(row.subtitle);
            binding.tvSubtitle.setVisibility(row.subtitle.isEmpty() ? View.GONE : View.VISIBLE);

            if (row.pill.isEmpty() || row.pillStyle == PILL_NONE) {
                binding.tvPill.setVisibility(View.GONE);
            } else {
                binding.tvPill.setVisibility(View.VISIBLE);
                binding.tvPill.setText(row.pill);
                binding.tvPill.setBackgroundResource(row.pillStyle == PILL_ALERT
                        ? R.drawable.bg_pill_alert
                        : row.pillStyle == PILL_NEUTRAL ? R.drawable.bg_pill_neutral
                        : R.drawable.bg_pill);
            }

            boolean hasPrimary = !row.primary.isEmpty();
            boolean hasSecondary = !row.secondary.isEmpty();
            binding.actions.setVisibility(hasPrimary || hasSecondary ? View.VISIBLE : View.GONE);
            binding.btnPrimary.setVisibility(hasPrimary ? View.VISIBLE : View.GONE);
            binding.btnPrimary.setText(row.primary);
            binding.btnPrimary.setOnClickListener(v -> listener.onPrimary(row));
            binding.btnSecondary.setVisibility(hasSecondary ? View.VISIBLE : View.GONE);
            binding.btnSecondary.setText(row.secondary);
            binding.btnSecondary.setOnClickListener(v -> listener.onSecondary(row));

            binding.getRoot().setClickable(row.clickable);
            binding.getRoot().setFocusable(row.clickable);
            if (row.clickable) {
                binding.getRoot().setOnClickListener(v -> listener.onRowClick(row));
            } else {
                binding.getRoot().setOnClickListener(null);
                binding.getRoot().setClickable(false);
            }
        }
    }
}

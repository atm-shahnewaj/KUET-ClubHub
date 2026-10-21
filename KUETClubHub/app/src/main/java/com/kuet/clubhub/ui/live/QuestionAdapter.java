package com.kuet.clubhub.ui.live;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.kuet.clubhub.R;
import com.kuet.clubhub.databinding.ItemQuestionBinding;
import com.kuet.clubhub.model.Question;

import java.util.Objects;

/**
 * The Q&A board. Each card has an upvote button with the count on the left; the order of the
 * cards (most upvoted first, answered last) is decided by QuestionRanker before the list gets here.
 */
public class QuestionAdapter extends ListAdapter<QuestionAdapter.Item, QuestionAdapter.Holder> {

    /** A question plus what this viewer is allowed to do with it. */
    public static final class Item {
        final Question question;
        final boolean upvoted;
        final boolean canUpvote;
        final boolean canDelete;
        final boolean manager;

        public Item(Question question, boolean upvoted, boolean canUpvote,
                    boolean canDelete, boolean manager) {
            this.question = question;
            this.upvoted = upvoted;
            this.canUpvote = canUpvote;
            this.canDelete = canDelete;
            this.manager = manager;
        }
    }

    public interface Listener {
        /** upvote is the state to move to: true adds my upvote, false takes it back. */
        void onUpvote(Question question, boolean upvote);

        void onAnswered(Question question, boolean answered);

        void onDelete(Question question);
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<Item>() {
        @Override
        public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
            return Objects.equals(a.question.getId(), b.question.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
            return Objects.equals(a.question.getText(), b.question.getText())
                    && Objects.equals(a.question.getAuthorName(), b.question.getAuthorName())
                    && a.question.isAnonymous() == b.question.isAnonymous()
                    && a.question.isAnswered() == b.question.isAnswered()
                    && a.question.upvotes() == b.question.upvotes()
                    && Objects.equals(a.question.createdMillis(), b.question.createdMillis())
                    && a.upvoted == b.upvoted
                    && a.canUpvote == b.canUpvote
                    && a.canDelete == b.canDelete
                    && a.manager == b.manager;
        }
    };

    private final Listener listener;

    public QuestionAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemQuestionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemQuestionBinding binding;

        Holder(ItemQuestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Item item, Listener listener) {
            final Question q = item.question;
            final Context context = itemView.getContext();

            binding.tvText.setText(q.getText());
            binding.tvMeta.setText(context.getString(R.string.question_meta, author(context, q), asked(context, q)));
            binding.tvAnswered.setVisibility(q.isAnswered() ? View.VISIBLE : View.GONE);
            binding.getRoot().setAlpha(q.isAnswered() ? 0.75f : 1f);

            // The upvote button: filled when I have upvoted, plain when not, dimmed when I cannot vote.
            binding.btnUpvote.setText(context.getString(R.string.upvote_count, q.upvotes()));
            int primary = MaterialColors.getColor(context,
                    androidx.appcompat.R.attr.colorPrimary, 0xFF00873F);
            int container = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorPrimaryContainer, 0xFFC5F0D3);
            int onContainer = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF00210E);
            if (item.upvoted) {
                binding.btnUpvote.setBackgroundTintList(ColorStateList.valueOf(container));
                binding.btnUpvote.setTextColor(onContainer);
            } else {
                binding.btnUpvote.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                binding.btnUpvote.setTextColor(primary);
            }
            binding.btnUpvote.setEnabled(item.canUpvote || item.upvoted);
            binding.btnUpvote.setAlpha(item.canUpvote || item.upvoted ? 1f : 0.6f);
            binding.btnUpvote.setOnClickListener(v -> listener.onUpvote(q, !item.upvoted));

            binding.btnAnswered.setVisibility(item.manager ? View.VISIBLE : View.GONE);
            binding.btnAnswered.setText(q.isAnswered() ? R.string.action_unmark_answered
                    : R.string.action_mark_answered);
            binding.btnAnswered.setOnClickListener(v -> listener.onAnswered(q, !q.isAnswered()));

            binding.btnDelete.setVisibility(item.canDelete ? View.VISIBLE : View.GONE);
            binding.btnDelete.setOnClickListener(v -> listener.onDelete(q));

            binding.actions.setVisibility(item.manager || item.canDelete ? View.VISIBLE : View.GONE);
        }

        private static String author(Context context, Question q) {
            String name = q.getAuthorName();
            if (q.isAnonymous() || name == null || name.trim().isEmpty()) {
                return context.getString(R.string.anonymous);
            }
            return name;
        }

        private static String asked(Context context, Question q) {
            long created = q.createdMillis();
            long now = System.currentTimeMillis();
            if (created == Long.MAX_VALUE || now - created < DateUtils.MINUTE_IN_MILLIS) {
                return context.getString(R.string.just_now);
            }
            return DateUtils.getRelativeTimeSpanString(created, now, DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE).toString();
        }
    }
}

package com.kuet.clubhub.ui.live;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.kuet.clubhub.R;
import com.kuet.clubhub.databinding.ItemPollBinding;
import com.kuet.clubhub.model.Poll;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Shows the polls of an event. What a card shows depends on who is looking:
 * <ul>
 *   <li>a checked-in student who has not voted yet sees the options as buttons (results stay
 *       hidden so they do not sway the vote);</li>
 *   <li>after voting, or once the poll is closed, everyone sees the live bar chart;</li>
 *   <li>organizers always see the chart, with buttons to close, reopen or delete the poll.</li>
 * </ul>
 */
public class PollAdapter extends ListAdapter<PollAdapter.Item, PollAdapter.Holder> {

    /** A poll plus what this viewer is allowed to do with it. */
    public static final class Item {
        final Poll poll;
        /** The option this viewer voted for, or -1. */
        final int myChoice;
        final boolean canVote;
        final boolean manager;

        public Item(Poll poll, int myChoice, boolean canVote, boolean manager) {
            this.poll = poll;
            this.myChoice = myChoice;
            this.canVote = canVote;
            this.manager = manager;
        }
    }

    public interface Listener {
        void onVote(Poll poll, int optionIndex);

        void onToggleOpen(Poll poll);

        void onDelete(Poll poll);
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<Item>() {
        @Override
        public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
            return Objects.equals(a.poll.getId(), b.poll.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
            return Objects.equals(a.poll.getQuestion(), b.poll.getQuestion())
                    && Objects.equals(a.poll.getOptions(), b.poll.getOptions())
                    && Arrays.equals(a.poll.tally(), b.poll.tally())
                    && Objects.equals(a.poll.getStatus(), b.poll.getStatus())
                    && a.myChoice == b.myChoice
                    && a.canVote == b.canVote
                    && a.manager == b.manager;
        }
    };

    private final Listener listener;

    public PollAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPollBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ItemPollBinding binding;

        Holder(ItemPollBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Item item, Listener listener) {
            final Poll poll = item.poll;
            final Context context = itemView.getContext();
            final boolean open = poll.acceptingVotes();

            binding.tvQuestion.setText(poll.getQuestion());
            binding.tvStatus.setText(open ? R.string.poll_live : R.string.poll_closed);
            binding.tvStatus.setBackgroundResource(open ? R.drawable.bg_pill : R.drawable.bg_pill_neutral);

            long total = poll.totalVotes();
            binding.tvMeta.setText(context.getResources().getQuantityString(
                    R.plurals.poll_votes, (int) Math.min(total, Integer.MAX_VALUE), (int) Math.min(total, Integer.MAX_VALUE)));

            // Voting buttons: only for a checked-in student who has not voted, while the poll is open.
            boolean showButtons = item.canVote && item.myChoice < 0 && open && !item.manager;
            binding.optionContainer.removeAllViews();
            binding.optionContainer.setVisibility(showButtons ? View.VISIBLE : View.GONE);
            if (showButtons) {
                List<String> options = poll.getOptions();
                for (int i = 0; i < options.size(); i++) {
                    binding.optionContainer.addView(optionButton(context, options.get(i), poll, i, listener));
                }
            }

            // Results: everybody except a student who still has to vote.
            binding.bars.setVisibility(showButtons ? View.GONE : View.VISIBLE);
            if (!showButtons) {
                binding.bars.setData(poll.getId(), poll.getOptions(), poll.tally(), item.myChoice);
            }

            int hint = 0;
            if (!open) {
                hint = R.string.poll_hint_closed;
            } else if (item.manager) {
                hint = 0;
            } else if (item.myChoice >= 0) {
                hint = R.string.poll_hint_voted;
            } else if (item.canVote) {
                hint = R.string.poll_hint_can_vote;
            } else {
                hint = R.string.poll_hint_guest;
            }
            binding.tvHint.setVisibility(hint == 0 ? View.GONE : View.VISIBLE);
            if (hint != 0) {
                binding.tvHint.setText(hint);
            }

            binding.managerActions.setVisibility(item.manager ? View.VISIBLE : View.GONE);
            binding.btnToggle.setText(open ? R.string.action_close_poll : R.string.action_reopen_poll);
            binding.btnToggle.setOnClickListener(v -> listener.onToggleOpen(poll));
            binding.btnDelete.setOnClickListener(v -> listener.onDelete(poll));
        }

        private static MaterialButton optionButton(Context context, String text, Poll poll,
                                                   int index, Listener listener) {
            MaterialButton button = new MaterialButton(context, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(8 * context.getResources().getDisplayMetrics().density);
            button.setLayoutParams(lp);
            button.setText(text);
            button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            button.setOnClickListener(v -> listener.onVote(poll, index));
            return button;
        }
    }
}

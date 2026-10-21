package com.kuet.clubhub.ui.live;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.kuet.clubhub.util.PollMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A hand-drawn horizontal bar chart for one poll (a custom View, drawn on a Canvas). Each option
 * gets a label with its vote count and percentage, and a rounded bar whose length is its share of
 * all votes. When new totals arrive the bars slide to their new length, so a live vote is easy to
 * see. The option the viewer voted for is drawn in bold with a tick.
 */
public class PollBarsView extends View {

    private final List<String> labels = new ArrayList<>();
    private long[] tally = new long[0];
    private long total = 0;
    private int myChoice = -1;
    /** The poll currently drawn; bars animate only between two states of the same poll. */
    @Nullable
    private String pollKey;

    /** Bar lengths currently on screen, and the ones they are sliding to (0 to 1). */
    private float[] shown = new float[0];
    private float[] from = new float[0];
    private float[] to = new float[0];
    @Nullable
    private ValueAnimator animator;

    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final float barHeight;
    private final float labelGap;
    private final float rowGap;
    private final float valueGap;

    public PollBarsView(Context context) {
        this(context, null);
    }

    public PollBarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        barHeight = dp(12);
        labelGap = dp(6);
        rowGap = dp(14);
        valueGap = dp(10);

        labelPaint.setTextSize(sp(14));
        valuePaint.setTextSize(sp(13));
        valuePaint.setTextAlign(Paint.Align.RIGHT);

        int onSurface = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, 0xFF181D19);
        int onSurfaceVariant = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF404942);
        labelPaint.setColor(onSurface);
        valuePaint.setColor(onSurfaceVariant);
        trackPaint.setColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOutlineVariant, 0xFFBFC9C0));
        barPaint.setColor(MaterialColors.getColor(context,
                androidx.appcompat.R.attr.colorPrimary, 0xFF00873F));
    }

    /**
     * Shows a poll. key identifies the poll (its id), votes[i] is the votes of option i, and
     * myChoice is the option the viewer voted for, or -1. If only the numbers of the same poll
     * changed, the bars animate to the new lengths; a different poll is drawn straight away.
     */
    public void setData(String key, List<String> optionLabels, long[] votes, int myChoice) {
        boolean sameShape = key != null && key.equals(pollKey)
                && optionLabels.size() == labels.size() && votes.length == tally.length;
        pollKey = key;
        labels.clear();
        labels.addAll(optionLabels);
        this.tally = votes.clone();
        this.myChoice = myChoice;
        long sum = 0;
        for (long v : votes) {
            sum += v;
        }
        this.total = sum;

        float[] target = new float[votes.length];
        for (int i = 0; i < votes.length; i++) {
            target[i] = PollMath.fraction(votes[i], total);
        }
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (!sameShape || shown.length != target.length) {
            shown = target.clone();
            to = target;
            from = target.clone();
        } else {
            from = shown.clone();
            to = target;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(350);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                for (int i = 0; i < shown.length; i++) {
                    shown[i] = from[i] + (to[i] - from[i]) * t;
                }
                invalidate();
            });
            animator.start();
        }
        setContentDescription(describe());
        requestLayout();
        invalidate();
    }

    private String describe() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(labels.get(i)).append(": ").append(tally[i]).append(" votes, ")
                    .append(PollMath.percent(tally[i], total)).append(" percent");
            if (i == myChoice) {
                sb.append(", your vote");
            }
        }
        return sb.toString();
    }

    private float labelHeight() {
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    private float rowHeight() {
        return labelHeight() + labelGap + barHeight + rowGap;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int rows = labels.size();
        float content = rows == 0 ? 0 : rows * rowHeight() - rowGap;
        int height = (int) Math.ceil(getPaddingTop() + content + getPaddingBottom());
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int rows = labels.size();
        if (rows == 0) {
            return;
        }
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float fullWidth = right - left;
        float labelH = labelHeight();
        float labelAscent = -labelPaint.getFontMetrics().ascent;
        float rowH = rowHeight();

        for (int i = 0; i < rows; i++) {
            float top = getPaddingTop() + i * rowH;

            String value = String.format(Locale.getDefault(), "%d · %d%%",
                    tally[i], PollMath.percent(tally[i], total));
            canvas.drawText(value, right, top + labelAscent, valuePaint);

            boolean mine = i == myChoice;
            labelPaint.setFakeBoldText(mine);
            String text = (mine ? "✓ " : "") + labels.get(i);
            float available = Math.max(0f, fullWidth - valuePaint.measureText(value) - valueGap);
            CharSequence fitted = TextUtils.ellipsize(text, labelPaint, available,
                    TextUtils.TruncateAt.END);
            canvas.drawText(fitted, 0, fitted.length(), left, top + labelAscent, labelPaint);

            float barTop = top + labelH + labelGap;
            rect.set(left, barTop, right, barTop + barHeight);
            canvas.drawRoundRect(rect, barHeight / 2, barHeight / 2, trackPaint);

            float fraction = i < shown.length ? shown[i] : 0f;
            if (tally[i] > 0) {
                // never thinner than a dot, so one vote is still visible
                float fill = Math.max(barHeight, fraction * fullWidth);
                rect.set(left, barTop, left + fill, barTop + barHeight);
                canvas.drawRoundRect(rect, barHeight / 2, barHeight / 2, barPaint);
            }
        }
        labelPaint.setFakeBoldText(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }
}

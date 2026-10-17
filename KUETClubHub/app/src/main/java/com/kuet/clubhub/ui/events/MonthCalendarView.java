package com.kuet.clubhub.ui.events;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A hand-drawn month calendar: weekday header, a 7 x 6 grid of days, a highlighted selected day,
 * a ring around today, and small coloured dots under days that have events.
 */
public class MonthCalendarView extends View {

    public interface OnDayClickListener {
        void onDayClick(int year, int month, int day);
    }

    private static final int ROWS = 6;
    private static final int COLS = 7;
    private static final int MAX_DOTS = 3;

    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float density;
    private final float headerHeight;
    private final float dotRadius;

    private int colorPrimary;
    private int colorOnPrimary;
    private int colorOnSurface;
    private int colorMuted;

    private int year;
    private int month; // 0-based, like java.util.Calendar
    private int selectedDay = -1;
    private int firstDayOffset;
    private int daysInMonth;
    private int firstDayOfWeek;
    private Map<Integer, List<Integer>> dots = new HashMap<>();
    private OnDayClickListener listener;

    public MonthCalendarView(Context context) {
        this(context, null);
    }

    public MonthCalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        headerHeight = 32 * density;
        dotRadius = 2.8f * density;

        colorPrimary = MaterialColors.getColor(context,
                androidx.appcompat.R.attr.colorPrimary, 0xFF00873F);
        colorOnPrimary = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF);
        colorOnSurface = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, 0xFF181D19);
        colorMuted = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF404942);

        dayPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setTextSize(15 * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(12 * density);
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        labelPaint.setColor(colorMuted);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1.8f * density);
        ringPaint.setColor(colorPrimary);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(colorPrimary);

        Calendar now = Calendar.getInstance();
        setMonth(now.get(Calendar.YEAR), now.get(Calendar.MONTH));
    }

    // ------------------------------------------------------------------ public API

    public void setMonth(int year, int month) {
        this.year = year;
        this.month = month;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, 1);
        firstDayOfWeek = c.getFirstDayOfWeek();
        int dow = c.get(Calendar.DAY_OF_WEEK);
        firstDayOffset = (dow - firstDayOfWeek + 7) % 7;
        daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (selectedDay > daysInMonth) {
            selectedDay = -1;
        }
        invalidate();
    }

    public void setSelectedDay(int day) {
        this.selectedDay = day;
        invalidate();
    }

    /** Map of day-of-month to the dot colours to draw under that day. */
    public void setEventDots(Map<Integer, List<Integer>> dots) {
        this.dots = dots == null ? Collections.<Integer, List<Integer>>emptyMap() : dots;
        invalidate();
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.listener = listener;
    }

    // ------------------------------------------------------------------ measuring & drawing

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float cellHeight = cellHeightFor(width);
        int height = (int) (headerHeight + cellHeight * ROWS + 4 * density);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    private float cellHeightFor(int width) {
        return Math.min(52 * density, (width / (float) COLS) * 1.05f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cellW = getWidth() / (float) COLS;
        float cellH = cellHeightFor(getWidth());

        // weekday header
        String[] symbols = DateFormatSymbols.getInstance().getShortWeekdays();
        for (int c = 0; c < COLS; c++) {
            int weekday = ((firstDayOfWeek - 1 + c) % 7) + 1; // 1 = Sunday
            String label = symbols[weekday];
            if (label.length() > 3) {
                label = label.substring(0, 3);
            }
            canvas.drawText(label, cellW * c + cellW / 2f, headerHeight * 0.68f, labelPaint);
        }

        Calendar today = Calendar.getInstance();
        boolean isCurrentMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month;
        int todayDay = today.get(Calendar.DAY_OF_MONTH);
        float radius = Math.min(cellW, cellH) * 0.36f;

        for (int day = 1; day <= daysInMonth; day++) {
            int index = firstDayOffset + day - 1;
            int row = index / COLS;
            int col = index % COLS;
            float cx = cellW * col + cellW / 2f;
            float cy = headerHeight + cellH * row + cellH * 0.42f;

            boolean selected = day == selectedDay;
            if (selected) {
                canvas.drawCircle(cx, cy, radius, fillPaint);
            } else if (isCurrentMonth && day == todayDay) {
                canvas.drawCircle(cx, cy, radius, ringPaint);
            }

            dayPaint.setColor(selected ? colorOnPrimary : colorOnSurface);
            dayPaint.setTypeface(selected || (isCurrentMonth && day == todayDay)
                    ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            float textY = cy - (dayPaint.descent() + dayPaint.ascent()) / 2f;
            canvas.drawText(String.valueOf(day), cx, textY, dayPaint);

            List<Integer> colors = dots.get(day);
            if (colors != null && !colors.isEmpty()) {
                int count = Math.min(colors.size(), MAX_DOTS);
                float gap = dotRadius * 2.6f;
                float startX = cx - gap * (count - 1) / 2f;
                float dotY = cy + radius + dotRadius + 1.5f * density;
                for (int i = 0; i < count; i++) {
                    dotPaint.setColor(colors.get(i));
                    canvas.drawCircle(startX + gap * i, dotY, dotRadius, dotPaint);
                }
            }
        }
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float cellW = getWidth() / (float) COLS;
            float cellH = cellHeightFor(getWidth());
            float y = event.getY() - headerHeight;
            if (y >= 0) {
                int row = (int) (y / cellH);
                int col = (int) (event.getX() / cellW);
                int day = row * COLS + col - firstDayOffset + 1;
                if (row < ROWS && col < COLS && day >= 1 && day <= daysInMonth) {
                    selectedDay = day;
                    invalidate();
                    performClick();
                    if (listener != null) {
                        listener.onDayClick(year, month, day);
                    }
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}

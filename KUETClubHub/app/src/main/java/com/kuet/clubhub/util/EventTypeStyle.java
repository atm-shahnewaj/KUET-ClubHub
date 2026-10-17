package com.kuet.clubhub.util;

import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.kuet.clubhub.model.EventType;

/** Styles a small pill TextView with the colour and label of an event type. */
public final class EventTypeStyle {

    private EventTypeStyle() {
    }

    public static void apply(TextView pill, EventType type) {
        int color = ContextCompat.getColor(pill.getContext(), type.getColorRes());
        pill.setText(type.getLabel());
        pill.setBackgroundTintList(ColorStateList.valueOf(color));
        pill.setTextColor(ColorUtils.calculateLuminance(color) > 0.4 ? 0xFF1B1B1B : 0xFFFFFFFF);
    }
}

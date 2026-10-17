package com.kuet.clubhub.util;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.function.Consumer;

public final class PickerUtil {

    private PickerUtil() {
    }

    /** Shows a single-choice dialog and reports the picked item. */
    public static void showChoice(Context context, String title, String[] items,
                                  String current, Consumer<String> onPicked) {
        int checked = Arrays.asList(items).indexOf(current);
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    onPicked.accept(items[which]);
                    dialog.dismiss();
                })
                .show();
    }
}

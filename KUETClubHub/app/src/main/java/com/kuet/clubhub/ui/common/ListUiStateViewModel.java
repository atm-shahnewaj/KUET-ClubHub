package com.kuet.clubhub.ui.common;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

/**
 * Keeps the screen choices of a list fragment (which scope is open, which category chip is
 * selected) alive across rotation. A ViewModel survives the Activity being recreated, so the
 * person returns to the same filtered list after turning the phone.
 */
public class ListUiStateViewModel extends ViewModel {

    public static final int UNSET = -1;

    /** Club list scope (all / mine / pending), {@link #UNSET} until the person changes it. */
    public int mode = UNSET;

    /** Selected category chip; null means "All". */
    @Nullable
    public String category = null;
}

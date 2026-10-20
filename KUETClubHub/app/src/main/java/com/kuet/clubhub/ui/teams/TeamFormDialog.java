package com.kuet.clubhub.ui.teams;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kuet.clubhub.R;
import com.kuet.clubhub.databinding.DialogTeamFormBinding;
import com.kuet.clubhub.model.Team;
import com.kuet.clubhub.util.Interests;

import java.util.List;

/** The form for creating a team and for the leader editing it. */
final class TeamFormDialog {

    interface OnSubmit {
        void onSubmit(String name, List<String> skillsNeeded, String note, boolean open);
    }

    private static final int MIN_NAME = 3;
    private static final int MAX_SKILLS = 8;

    private TeamFormDialog() {
    }

    /** existing is null when creating a new team. */
    static void show(Context context, LayoutInflater inflater, @Nullable Team existing,
                     OnSubmit onSubmit) {
        final DialogTeamFormBinding form = DialogTeamFormBinding.inflate(inflater);
        if (existing != null) {
            form.etTeamName.setText(existing.getName());
            form.etTeamSkills.setText(Interests.join(existing.getSkillsNeeded()));
            form.etTeamNote.setText(existing.getNote());
            form.switchOpen.setChecked(existing.isOpen());
        }

        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(existing == null ? R.string.dialog_create_team_title
                        : R.string.dialog_edit_team_title)
                .setView(form.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(existing == null ? R.string.action_create : R.string.action_save,
                        null)
                .create();
        // The positive button is wired after show() so a validation error keeps the dialog open.
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = form.etTeamName.getText() == null ? ""
                            : form.etTeamName.getText().toString().trim();
                    if (name.length() < MIN_NAME) {
                        form.tilTeamName.setError(context.getString(R.string.error_team_name_short));
                        return;
                    }
                    form.tilTeamName.setError(null);
                    List<String> skills = Interests.parse(
                            form.etTeamSkills.getText() == null ? ""
                                    : form.etTeamSkills.getText().toString(), MAX_SKILLS);
                    String note = form.etTeamNote.getText() == null ? ""
                            : form.etTeamNote.getText().toString().trim();
                    dialog.dismiss();
                    onSubmit.onSubmit(name, skills, note, form.switchOpen.isChecked());
                }));
        dialog.show();
    }
}

package com.vpt.filemanager.browser.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import com.vpt.filemanager.R;

/** Collects a new file or folder name before invoking the create action. */
public final class CreateItemDialog {
    private CreateItemDialog() {
    }

    public interface OnConfirm {
        void onConfirm(boolean isFolder, String name);
    }

    public static void show(Context context, OnConfirm callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_create_item, null, false);
        TextInputEditText nameField = view.findViewById(R.id.et_name);
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggle_type);
        toggle.check(R.id.btn_type_folder);
        new AlertDialog.Builder(context)
                .setTitle(R.string.action_create)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    String name = nameField.getText() == null
                            ? "" : nameField.getText().toString().trim();
                    if (!name.isEmpty()) {
                        callback.onConfirm(toggle.getCheckedButtonId() == R.id.btn_type_folder,
                                name);
                    }
                })
                .show();
    }
}

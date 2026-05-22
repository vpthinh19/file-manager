package com.vpt.filemanager.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import com.vpt.filemanager.R;

/**
 * Dialog "Create new file/folder". Extract từ DualPaneHostFragment ở Phase R-5a.
 *
 * <p>Static show() pattern + callback interface — không state, không leak Fragment. Caller
 * truyền {@link Context} (typically Fragment.requireContext()) + nhận callback khi user confirm.
 *
 * <p>UX hiện tại được giữ y nguyên: TextInputLayout outlined box + MaterialButtonToggleGroup
 * (Folder / File). MaterialButtonToggleGroup không respect XML {@code android:checked} trên child
 * nên cần check Folder bằng code trong show().
 */
public final class CreateItemDialog {
    private CreateItemDialog() {
    }

    public interface OnConfirm {
        void onConfirm(boolean isFolder, String name);
    }

    public static void show(Context ctx, OnConfirm callback) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_create_item, null, false);
        TextInputEditText nameField = view.findViewById(R.id.et_name);
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggle_type);
        toggle.check(R.id.btn_type_folder);
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.action_create)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    String name = nameField.getText() == null
                            ? "" : nameField.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    boolean isFolder = toggle.getCheckedButtonId() == R.id.btn_type_folder;
                    callback.onConfirm(isFolder, name);
                })
                .show();
    }
}

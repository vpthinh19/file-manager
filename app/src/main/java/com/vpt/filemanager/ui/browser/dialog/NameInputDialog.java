package com.vpt.filemanager.ui.browser.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.StringRes;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.vpt.filemanager.R;

/**
 * Generic name-input dialog (rename, new name, ...). Reusable single-field TextInputLayout. Extract
 * từ DualPaneHostFragment.showNameDialog() ở Phase R-5a.
 */
public final class NameInputDialog {
    private NameInputDialog() {
    }

    public interface OnConfirm {
        void onConfirm(String name);
    }

    public static void show(Context ctx, @StringRes int titleRes, @StringRes int hintRes,
                            OnConfirm callback) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_name_input, null, false);
        TextInputLayout til = view.findViewById(R.id.til_name);
        TextInputEditText input = view.findViewById(R.id.et_name);
        til.setHint(hintRes);
        new AlertDialog.Builder(ctx)
                .setTitle(titleRes)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> callback.onConfirm(
                        input.getText() == null ? "" : input.getText().toString()))
                .show();
    }
}

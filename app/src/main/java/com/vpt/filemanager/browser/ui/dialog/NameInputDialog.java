package com.vpt.filemanager.browser.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.StringRes;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.vpt.filemanager.R;

/** Reusable one-field prompt used for rename and similar actions. */
public final class NameInputDialog {
    private NameInputDialog() {
    }

    public interface OnConfirm {
        void onConfirm(String name);
    }

    public static void show(Context context, @StringRes int titleRes, @StringRes int hintRes,
                            CharSequence initialValue, OnConfirm callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_name_input, null, false);
        TextInputLayout layout = view.findViewById(R.id.til_name);
        TextInputEditText input = view.findViewById(R.id.et_name);
        layout.setHint(hintRes);
        input.setText(initialValue);
        input.setSelection(0, editableNameEnd(initialValue));
        new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> callback.onConfirm(
                        input.getText() == null ? "" : input.getText().toString()))
                .show();
    }

    private static int editableNameEnd(CharSequence value) {
        String name = value == null ? "" : value.toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? dot : name.length();
    }
}

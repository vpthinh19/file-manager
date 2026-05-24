package com.vpt.filemanager.browser.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;

import com.vpt.filemanager.R;

/** Prompts for the resolution when an action would replace an existing entry. */
public final class ConflictDialog {
    private ConflictDialog() {
    }

    public interface OnChoice {
        void onReplace();

        void onKeepBoth();

        default void onCancel() {
        }
    }

    public static void show(Context context, String name, OnChoice callback) {
        final boolean[] resolved = {false};
        new AlertDialog.Builder(context)
                .setTitle(R.string.conflict_title)
                .setMessage(context.getString(R.string.conflict_message_format, name))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    resolved[0] = true;
                    callback.onCancel();
                })
                .setNeutralButton(R.string.conflict_keep_both, (dialog, which) -> {
                    resolved[0] = true;
                    callback.onKeepBoth();
                })
                .setPositiveButton(R.string.conflict_replace, (dialog, which) -> {
                    resolved[0] = true;
                    callback.onReplace();
                })
                .setOnDismissListener(dialog -> {
                    if (!resolved[0]) {
                        callback.onCancel();
                    }
                })
                .show();
    }
}

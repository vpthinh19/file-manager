package com.vpt.filemanager.component.dialog;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.StringRes;

public final class ConfirmDialogComponent {
    private ConfirmDialogComponent() {
    }

    public static void show(Context context, @StringRes int title, CharSequence message,
                            Runnable confirmed) {
        new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> confirmed.run())
                .show();
    }

    public static void show(Context context, @StringRes int title, @StringRes int message,
                            Runnable confirmed) {
        show(context, title, context.getString(message), confirmed);
    }
}

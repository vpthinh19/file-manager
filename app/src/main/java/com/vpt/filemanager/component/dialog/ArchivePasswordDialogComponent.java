package com.vpt.filemanager.component.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.textfield.TextInputEditText;
import com.vpt.filemanager.R;

import java.util.function.Consumer;

/** Requests a passphrase only when an encrypted archive read requires it. */
public final class ArchivePasswordDialogComponent {
    private ArchivePasswordDialogComponent() {
    }

    public static void show(Context context, String name, Consumer<String> accepted,
                            Runnable cancelled) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_archive_password, null);
        TextInputEditText password = content.findViewById(R.id.et_password);
        new AlertDialog.Builder(context).setTitle(R.string.archive_password_title)
                .setMessage(context.getString(R.string.archive_password_message, name))
                .setView(content)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> cancelled.run())
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        accepted.accept(password.getText() == null ? "" : password.getText().toString()))
                .show();
    }
}

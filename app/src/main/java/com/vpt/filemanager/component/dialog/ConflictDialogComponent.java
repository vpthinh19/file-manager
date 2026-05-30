package com.vpt.filemanager.component.dialog;

import android.app.AlertDialog;
import android.content.Context;

import com.vpt.filemanager.R;
import com.vpt.filemanager.storage.facade.TransferDecision;

import java.util.function.BiConsumer;

public final class ConflictDialogComponent {
    private ConflictDialogComponent() {
    }

    public static void show(Context context, String name,
                            BiConsumer<TransferDecision, Boolean> decided) {
        boolean[] applyAll = {false};
        new AlertDialog.Builder(context)
                .setTitle(R.string.conflict_title)
                .setMessage(context.getString(R.string.conflict_message_format, name))
                .setMultiChoiceItems(new String[] {context.getString(R.string.conflict_apply_all)},
                        null, (dialog, which, checked) -> applyAll[0] = checked)
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> decided.accept(TransferDecision.CANCEL, false))
                .setNeutralButton(R.string.conflict_keep_both,
                        (dialog, which) -> decided.accept(TransferDecision.KEEP_BOTH, applyAll[0]))
                .setPositiveButton(R.string.conflict_replace,
                        (dialog, which) -> decided.accept(TransferDecision.REPLACE, applyAll[0]))
                .show();
    }
}

package com.vpt.filemanager.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;

import com.vpt.filemanager.R;

/**
 * Phase 2C-6 KISS conflict dialog: Cancel / Keep both / Replace. Extract từ
 * DualPaneHostFragment.showCreateConflictDialog() ở Phase R-5a.
 *
 * <p>3-button AlertDialog: Negative=Cancel (no-op), Neutral=Keep both, Positive=Replace. Callback
 * interface tách 2 nhánh, caller cancel nhánh = không truyền.
 */
public final class ConflictDialog {
    private ConflictDialog() {
    }

    public interface OnChoice {
        void onReplace();

        void onKeepBoth();

        /**
         * Phase C-1b: Cancel signal cần thiết cho transfer batch (abort toàn bộ items còn lại).
         * Default no-op giữ backward-compat cho CreateAction (Cancel = ignore create).
         * Fired cả khi user click Cancel button, dismiss dialog, hoặc tap-outside.
         */
        default void onCancel() {
        }
    }

    public static void show(Context ctx, String name, OnChoice callback) {
        // Negative button + dismiss listener đều fire onCancel để Transfer flow biết user thoát.
        // CreateAction override mặc định no-op nên không bị ảnh hưởng.
        // Dùng cờ resolved để negative + onDismiss không fire onCancel 2 lần khi user click Cancel
        // (click → dismiss → 2 callbacks). Replace/KeepBoth set resolved=true trước khi dismiss.
        final boolean[] resolved = {false};
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.conflict_title)
                .setMessage(ctx.getString(R.string.conflict_message_format, name))
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    resolved[0] = true;
                    callback.onCancel();
                })
                .setNeutralButton(R.string.conflict_keep_both, (d, w) -> {
                    resolved[0] = true;
                    callback.onKeepBoth();
                })
                .setPositiveButton(R.string.conflict_replace, (d, w) -> {
                    resolved[0] = true;
                    callback.onReplace();
                })
                .setOnDismissListener(d -> {
                    if (!resolved[0]) {
                        callback.onCancel();
                    }
                })
                .show();
    }
}

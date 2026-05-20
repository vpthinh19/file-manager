package com.vpt.filemanager.ui.browser.dialog;

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
    }

    public static void show(Context ctx, String name, OnChoice callback) {
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.conflict_title)
                .setMessage(ctx.getString(R.string.conflict_message_format, name))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.conflict_keep_both, (d, w) -> callback.onKeepBoth())
                .setPositiveButton(R.string.conflict_replace, (d, w) -> callback.onReplace())
                .show();
    }
}

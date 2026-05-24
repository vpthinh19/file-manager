package com.vpt.filemanager.browser.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.vpt.filemanager.R;

/**
 * Android input surface for opening a search listing in the active pane.
 */
public final class SearchDialog {
    private SearchDialog() {
    }

    public interface OnSearch {
        void onSearch(String query);
    }

    public static void show(Context context, OnSearch callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_name_input, null, false);
        TextInputLayout layout = view.findViewById(R.id.til_name);
        TextInputEditText input = view.findViewById(R.id.et_name);
        layout.setHint(R.string.search_files_hint);
        new AlertDialog.Builder(context)
                .setTitle(R.string.action_search)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_search, (dialog, which) -> {
                    String query = input.getText() == null
                            ? "" : input.getText().toString().trim();
                    if (!query.isEmpty()) {
                        callback.onSearch(query);
                    }
                })
                .show();
    }
}

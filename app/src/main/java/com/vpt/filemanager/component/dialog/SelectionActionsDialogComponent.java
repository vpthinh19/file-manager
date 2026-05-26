package com.vpt.filemanager.component.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.IntConsumer;

public final class SelectionActionsDialogComponent {
    private static final float DISABLED_ALPHA = 0.38f;

    private SelectionActionsDialogComponent() {
    }

    public static void show(Context context, String title, String[] labels, boolean[] enabled,
                            IntConsumer selected) {
        new AlertDialog.Builder(context).setTitle(title)
                .setAdapter(new ActionAdapter(context, labels, enabled),
                        (dialog, index) -> selected.accept(index)).show();
    }

    private static final class ActionAdapter extends ArrayAdapter<String> {
        private final boolean[] enabledRows;

        ActionAdapter(Context context, String[] labels, boolean[] enabledRows) {
            super(context, android.R.layout.simple_list_item_1, labels);
            this.enabledRows = enabledRows;
        }

        @Override public boolean isEnabled(int position) {
            return enabledRows[position];
        }

        @Override @NonNull
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = super.getView(position, convertView, parent);
            row.setEnabled(isEnabled(position));
            row.setAlpha(isEnabled(position) ? 1f : DISABLED_ALPHA);
            return row;
        }
    }
}

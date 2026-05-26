package com.vpt.filemanager.component.dialog;

import android.app.AlertDialog;
import android.content.Context;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.entry.SortOption;

import java.util.function.Consumer;

public final class SortDialogComponent {
    private SortDialogComponent() {
    }

    public static void show(Context context, Consumer<SortOption> selected) {
        int[] labels = {R.string.sort_name_asc, R.string.sort_name_desc, R.string.sort_size_desc,
                R.string.sort_size_asc, R.string.sort_date_desc, R.string.sort_date_asc,
                R.string.sort_type};
        SortOption[] values = SortOption.values();
        String[] items = new String[labels.length];
        for (int index = 0; index < labels.length; index++) {
            items[index] = context.getString(labels[index]);
        }
        new AlertDialog.Builder(context).setTitle(R.string.sort_title)
                .setItems(items, (dialog, which) -> selected.accept(values[which])).show();
    }
}

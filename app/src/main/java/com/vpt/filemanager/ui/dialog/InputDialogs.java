package com.vpt.filemanager.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.StringRes;

import com.vpt.filemanager.R;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Small component-owned text input dialogs used by top and bottom bars. */
public final class InputDialogs {
    private InputDialogs() {
    }

    public static void prompt(Context context, @StringRes int title, @StringRes int hint,
                              String initial, Consumer<String> accepted) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_name_input, null);
        EditText name = content.findViewById(R.id.et_name);
        name.setHint(hint);
        name.setText(initial);
        name.selectAll();
        new AlertDialog.Builder(context).setTitle(title).setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        accepted.accept(name.getText().toString()))
                .show();
    }

    public static void create(Context context, BiConsumer<Boolean, String> accepted) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_create_item, null);
        EditText name = content.findViewById(R.id.et_name);
        com.google.android.material.button.MaterialButtonToggleGroup type =
                content.findViewById(R.id.toggle_type);
        type.check(R.id.btn_type_folder);
        new AlertDialog.Builder(context).setTitle(R.string.action_create).setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        accepted.accept(type.getCheckedButtonId() == R.id.btn_type_folder,
                                name.getText().toString()))
                .show();
    }
}

package com.vpt.filemanager.component.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.vpt.filemanager.R;
import com.vpt.filemanager.storage.virtual.archive.ArchiveCreateOptions;

import java.util.function.Consumer;

/** Dialog collecting the explicit settings needed to create a new archive. */
public final class ArchiveOptionsDialogComponent {
    private ArchiveOptionsDialogComponent() {
    }

    public static void show(Context context, String initialName, Consumer<Result> accepted) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_archive_options, null);
        TextInputEditText name = content.findViewById(R.id.et_archive_name);
        TextInputEditText password = content.findViewById(R.id.et_archive_password);
        TextInputLayout passwordLayout = content.findViewById(R.id.til_archive_password);
        Spinner format = content.findViewById(R.id.spinner_archive_format);
        Spinner level = content.findViewById(R.id.spinner_archive_level);
        name.setText(initialName);
        name.selectAll();

        ArchiveCreateOptions.Format[] formats = ArchiveCreateOptions.Format.values();
        ArchiveCreateOptions.Level[] levels = ArchiveCreateOptions.Level.values();
        format.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{context.getString(R.string.archive_format_zip),
                        context.getString(R.string.archive_format_7z),
                        context.getString(R.string.archive_format_tar_gz)}));
        level.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{context.getString(R.string.archive_level_store),
                        context.getString(R.string.archive_level_fast),
                        context.getString(R.string.archive_level_normal),
                        context.getString(R.string.archive_level_maximum)}));
        level.setSelection(2);
        format.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean supported = formats[position].supportsPassword();
                passwordLayout.setEnabled(supported);
                if (!supported) password.setText("");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        new AlertDialog.Builder(context).setTitle(R.string.compress_title).setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_compress, (dialog, which) -> {
                    String enteredPassword = password.getText() == null ? null
                            : password.getText().toString();
                    accepted.accept(new Result(name.getText() == null ? "" : name.getText().toString(),
                            new ArchiveCreateOptions(formats[format.getSelectedItemPosition()],
                                    levels[level.getSelectedItemPosition()], enteredPassword)));
                }).show();
    }

    public record Result(String name, ArchiveCreateOptions options) {
    }
}

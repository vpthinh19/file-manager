package com.vpt.filemanager.component.dialog;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.format.ExtensionRegistry;

import java.util.function.Consumer;

/** In-app presentation chooser for files without an extension. */
public final class OpenAsDialogComponent {
    private OpenAsDialogComponent() {
    }

    public static void show(@NonNull Context context, @NonNull String name,
                            @NonNull Consumer<ExtensionRegistry.Type> selected,
                            @NonNull Runnable cancelled) {
        int[] labelIds = {R.string.openas_text, R.string.openas_image, R.string.openas_video,
                R.string.openas_audio, R.string.openas_archive};
        ExtensionRegistry.Type[] modes = {ExtensionRegistry.Type.TEXT, ExtensionRegistry.Type.IMAGE,
                ExtensionRegistry.Type.VIDEO, ExtensionRegistry.Type.AUDIO,
                ExtensionRegistry.Type.ARCHIVE};
        String[] labels = new String[labelIds.length];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = context.getString(labelIds[index]);
        }
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.openas_title, name))
                .setItems(labels, (dialog, which) -> selected.accept(modes[which]))
                .setOnCancelListener(dialog -> cancelled.run())
                .show();
    }
}

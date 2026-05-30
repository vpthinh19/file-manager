package com.vpt.filemanager.component.dialog;

import android.app.AlertDialog;
import android.content.Context;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.format.ByteSize;

public final class PropertiesDialogComponent {
    private PropertiesDialogComponent() {
    }

    public static void show(Context context, Entry entry) {
        new AlertDialog.Builder(context).setTitle(R.string.properties)
                .setMessage(entry.name() + "\n" + entry.localPath() + "\n"
                        + ByteSize.format(entry.size()))
                .setPositiveButton(android.R.string.ok, null).show();
    }
}

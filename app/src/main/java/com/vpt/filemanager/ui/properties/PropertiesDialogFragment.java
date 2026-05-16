package com.vpt.filemanager.ui.properties;

import android.app.Dialog;
import android.os.Bundle;
import android.system.Os;
import android.system.StructStat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.util.ByteSize;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.PosixPermission;
import com.vpt.filemanager.ui.common.BaseDialogFragment;

public final class PropertiesDialogFragment extends BaseDialogFragment {
    private static final String ARG_PATH = "path";

    public static PropertiesDialogFragment newInstance(String path) {
        PropertiesDialogFragment fragment = new PropertiesDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String rawPath = requireArguments().getString(ARG_PATH, "");
        FilePath path = FilePath.parse(rawPath);
        File file = new File(path.path());
        String message = buildMessage(path, file);
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.properties)
                .setMessage(message)
                .setNeutralButton("MORE", null)
                .setNegativeButton("CHECKSUM", null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    private static String buildMessage(FilePath path, File file) {
        StringBuilder out = new StringBuilder();
        out.append("Name        ").append(file.getName().isEmpty() ? path.path() : file.getName()).append('\n');
        out.append("Parent      ").append(file.getParent() == null ? "/" : file.getParent()).append('\n');
        out.append("Type        ").append(file.isDirectory() ? "Folder" : "File").append('\n');
        out.append("Size        ").append(file.isDirectory() ? "-" : ByteSize.format(file.length()) + " (" + file.length() + ")").append('\n');
        out.append("Modified    ");
        out.append(file.lastModified() > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(file.lastModified()))
                : "Unavailable");
        out.append("\n\n");
        try {
            StructStat stat = Os.stat(path.path());
            PosixPermission permission = new PosixPermission(stat.st_mode);
            out.append("Permissions ").append(permission.toRwxString()).append(" (")
                    .append(Integer.toOctalString(stat.st_mode & 0777)).append(")\n");
            out.append("Owner       ").append(stat.st_uid).append('\n');
            out.append("Group       ").append(stat.st_gid);
        } catch (Exception e) {
            out.append("Permissions ").append("Unavailable").append('\n');
            out.append("Owner       ").append("Unavailable").append('\n');
            out.append("Group       ").append("Unavailable");
        }
        return out.toString();
    }
}

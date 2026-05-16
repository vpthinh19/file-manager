package com.vpt.filemanager.ui.browser;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.util.ByteSize;
import com.vpt.filemanager.domain.model.FileNode;

public final class FileViewHolder extends RecyclerView.ViewHolder {
    private final TextView icon;
    private final TextView name;
    private final TextView meta;

    public FileViewHolder(@NonNull View itemView) {
        super(itemView);
        icon = itemView.findViewById(R.id.tv_icon);
        name = itemView.findViewById(R.id.tv_name);
        meta = itemView.findViewById(R.id.tv_meta);
    }

    public void bind(FileNode node) {
        if (node instanceof ParentFileNode) {
            icon.setText("..");
            icon.setBackgroundResource(R.drawable.bg_icon_folder);
        } else if (node.isDirectory()) {
            icon.setText("DIR");
            icon.setBackgroundResource(R.drawable.bg_icon_folder);
        } else {
            icon.setText(labelFor(node.name()));
            icon.setBackgroundResource(backgroundFor(node.name()));
        }
        name.setText(node.name());
        name.setTextColor(0xFFE0E0E0);
        String size = node.isDirectory() ? "Folder" : ByteSize.format(node.sizeBytes());
        String date = node.lastModifiedMillis() > 0
                ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(node.lastModifiedMillis()))
                : "Parent";
        meta.setText(size + " - " + date);
        meta.setTextColor(0xFF9E9E9E);
    }

    private static String labelFor(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav")) {
            return "AUD";
        }
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".7z")) {
            return "ZIP";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")
                || lower.endsWith(".xml") || lower.endsWith(".prop") || lower.endsWith(".log")) {
            return "TXT";
        }
        return "DOC";
    }

    private static int backgroundFor(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".pdf")) {
            return R.drawable.bg_icon_pdf;
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav")) {
            return R.drawable.bg_icon_audio;
        }
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".7z")) {
            return R.drawable.bg_icon_archive;
        }
        return R.drawable.bg_icon_file;
    }
}

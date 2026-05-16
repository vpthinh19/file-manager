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

    public void bind(FileNode node, boolean selected) {
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
        itemView.setBackgroundColor(selected ? 0xFF1A3E5B : 0xFF303030);
    }

    private static String labelFor(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (isAudio(lower)) {
            return "AUD";
        }
        if (isVideo(lower)) {
            return "VID";
        }
        if (isImage(lower)) {
            return "IMG";
        }
        if (isArchive(lower)) {
            return "ZIP";
        }
        if (isSourceCode(lower)) {
            return "SRC";
        }
        if (isText(lower)) {
            return "TXT";
        }
        if (lower.endsWith(".apk")) {
            return "APK";
        }
        return "DOC";
    }

    private static int backgroundFor(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".pdf")) {
            return R.drawable.bg_icon_pdf;
        }
        if (isAudio(lower)) {
            return R.drawable.bg_icon_audio;
        }
        if (isArchive(lower)) {
            return R.drawable.bg_icon_archive;
        }
        return R.drawable.bg_icon_file;
    }

    private static boolean isAudio(String lower) {
        return endsWithAny(lower, ".mp3", ".flac", ".wav", ".ogg", ".m4a", ".aac", ".opus", ".wma");
    }

    private static boolean isVideo(String lower) {
        return endsWithAny(lower, ".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".flv", ".wmv", ".ts");
    }

    private static boolean isImage(String lower) {
        return endsWithAny(lower, ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif",
                ".avif", ".svg", ".ico", ".tiff", ".tif");
    }

    private static boolean isArchive(String lower) {
        return endsWithAny(lower, ".zip", ".tar", ".gz", ".bz2", ".xz", ".7z", ".rar",
                ".tgz", ".tbz", ".txz", ".jar", ".war", ".cpio", ".ar");
    }

    private static boolean isSourceCode(String lower) {
        return endsWithAny(lower,
                ".java", ".kt", ".kts", ".scala", ".groovy",
                ".py", ".rb", ".php", ".pl", ".lua",
                ".c", ".h", ".cpp", ".hpp", ".cc", ".cxx", ".m", ".mm",
                ".rs", ".go", ".swift", ".dart", ".ex", ".exs",
                ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".vue", ".svelte",
                ".css", ".scss", ".sass", ".less",
                ".sh", ".bash", ".zsh", ".fish", ".ps1", ".bat", ".cmd",
                ".sql", ".r", ".jl", ".clj", ".hs", ".erl");
    }

    private static boolean isText(String lower) {
        return endsWithAny(lower, ".txt", ".md", ".markdown", ".rst", ".log", ".csv", ".tsv",
                ".json", ".xml", ".yaml", ".yml", ".toml", ".ini", ".conf", ".cfg", ".prop",
                ".properties", ".env", ".gradle", ".lock", ".html", ".htm");
    }

    private static boolean endsWithAny(String value, String... suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}

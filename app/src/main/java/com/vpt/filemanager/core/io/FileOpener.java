package com.vpt.filemanager.core.io;

import com.vpt.filemanager.core.util.MimeTypes;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.MimeCategory;

public final class FileOpener {
    private FileOpener() {
    }

    public enum Action { OPEN_IMAGE, OPEN_VIDEO, OPEN_AUDIO, OPEN_TEXT, OPEN_ARCHIVE, OPEN_WITH }

    public static Action decide(FileNode node) {
        if (node == null || node.isDirectory()) {
            return Action.OPEN_WITH;
        }
        String name = node.name();
        MimeCategory category = MimeTypes.category(null, name);
        switch (category) {
            case IMAGE:
                return Action.OPEN_IMAGE;
            case VIDEO:
                return Action.OPEN_VIDEO;
            case AUDIO:
                return Action.OPEN_AUDIO;
            case TEXT:
                return Action.OPEN_TEXT;
            case ARCHIVE:
                return Action.OPEN_ARCHIVE;
            case OTHER:
            default:
                if (looksLikeText(name)) {
                    return Action.OPEN_TEXT;
                }
                return Action.OPEN_WITH;
        }
    }

    private static boolean looksLikeText(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        String[] textExtensions = {
                ".md", ".log", ".prop", ".cfg", ".conf", ".ini", ".sh", ".bat",
                ".java", ".kt", ".py", ".rb", ".rs", ".go", ".c", ".cpp", ".h",
                ".js", ".ts", ".css", ".scss", ".sql", ".yml", ".yaml", ".toml",
                ".gradle", ".kts", ".csv", ".tsv"
        };
        for (String ext : textExtensions) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        int dot = lower.lastIndexOf('.');
        return dot < 0;
    }
}

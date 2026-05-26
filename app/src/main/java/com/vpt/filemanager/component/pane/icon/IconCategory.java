package com.vpt.filemanager.component.pane.icon;

import java.util.Locale;

/**
 * Visual icon bucket only; opening behavior is determined later from file contents.
 *
 * <p>One icon per intuitive file kind: a user sees "document" or "spreadsheet" regardless of
 * whether the extension is .docx, .doc, .md, or .txt. Per-extension brand colors removed (Option D)
 * because they fragmented the rail with too many distinct tints and required ext-text overlays that
 * overflowed on long extensions (e.g. DOCX, MARKDOWN).
 */
public enum IconCategory {
    FOLDER,
    DOCUMENT,
    SHEET,
    SLIDE,
    PDF,
    ARCHIVE,
    IMAGE,
    VIDEO,
    AUDIO,
    APK,
    CODE,
    UNKNOWN;

    /**
     * Maps a file name (extension only — directory check is the caller's job) to an icon bucket.
     * Folder rows must call {@link #FOLDER} directly via {@link FileIconView#bindFolder()}.
     */
    public static IconCategory ofFileName(String name) {
        if (name == null || name.isEmpty()) {
            return UNKNOWN;
        }
        String lower = name.toLowerCase(Locale.US);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            return UNKNOWN;
        }
        if (lower.endsWith(".pdf")) return PDF;
        if (lower.endsWith(".apk")) return APK;
        if (endsWithAny(lower, AUDIO_EXT)) return AUDIO;
        if (endsWithAny(lower, VIDEO_EXT)) return VIDEO;
        if (endsWithAny(lower, IMAGE_EXT)) return IMAGE;
        if (endsWithAny(lower, ARCHIVE_EXT)) return ARCHIVE;
        if (endsWithAny(lower, SHEET_EXT)) return SHEET;
        if (endsWithAny(lower, SLIDE_EXT)) return SLIDE;
        if (endsWithAny(lower, DOCUMENT_EXT)) return DOCUMENT;
        if (endsWithAny(lower, CODE_EXT)) return CODE;
        return UNKNOWN;
    }

    private static boolean endsWithAny(String value, String[] suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) return true;
        }
        return false;
    }

    private static final String[] AUDIO_EXT = {
            ".mp3", ".flac", ".wav", ".ogg", ".m4a", ".aac", ".opus", ".wma", ".amr", ".mid", ".midi"
    };

    private static final String[] VIDEO_EXT = {
            ".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".flv", ".wmv", ".ts", ".m4v", ".mpg", ".mpeg"
    };

    private static final String[] IMAGE_EXT = {
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp",
            ".heic", ".heif", ".avif", ".svg", ".ico", ".tiff", ".tif"
    };

    private static final String[] ARCHIVE_EXT = {
            ".zip", ".tar", ".gz", ".bz2", ".xz", ".zst", ".7z", ".rar", ".rar5",
            ".tgz", ".tbz", ".tbz2", ".txz", ".tzst", ".jar", ".war", ".cpio",
            ".ar", ".aar", ".xar", ".cab", ".iso", ".lha", ".lzh", ".warc"
    };

    private static final String[] SHEET_EXT = {
            ".xls", ".xlsx", ".ods", ".csv", ".tsv"
    };

    private static final String[] SLIDE_EXT = {
            ".ppt", ".pptx", ".odp", ".key"
    };

    /** Anything a human treats as "a document I read or edit": prose, notes, configs, logs. */
    private static final String[] DOCUMENT_EXT = {
            ".doc", ".docx", ".odt", ".rtf", ".epub", ".mobi",
            ".txt", ".md", ".markdown", ".rst", ".log",
            ".prop", ".properties", ".conf", ".cfg", ".ini", ".env",
            ".gradle", ".lock"
    };

    /** Source code + structured config/data — gets a {@code </>} braces glyph. */
    private static final String[] CODE_EXT = {
            ".java", ".kt", ".kts", ".scala", ".groovy",
            ".py", ".rb", ".php", ".pl", ".lua",
            ".c", ".h", ".cpp", ".hpp", ".cc", ".cxx", ".m", ".mm",
            ".rs", ".go", ".swift", ".dart", ".ex", ".exs", ".cs",
            ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".vue", ".svelte",
            ".html", ".htm", ".css", ".scss", ".sass", ".less",
            ".json", ".xml", ".yaml", ".yml", ".toml",
            ".sh", ".bash", ".zsh", ".fish", ".ps1", ".bat", ".cmd",
            ".sql", ".r", ".jl", ".clj", ".hs", ".erl"
    };
}

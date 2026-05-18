package com.vpt.filemanager.domain.model;

import java.util.Locale;

/**
 * Visual category for file icons / badge labels. Distinct from {@link MimeCategory} on purpose:
 * MimeCategory drives the "open with" decision (5 broad buckets), whereas FileCategory drives the
 * row-icon rendering and therefore needs finer granularity (PDF vs DOC, APK vs ARCHIVE, CODE vs
 * TEXT).
 *
 * <p>Pure Java, no Android imports — testable and the single source of truth for category mapping.
 * The UI layer maps each value to a drawable + color resource (see {@code FileLabel}).
 */
public enum FileCategory {
    TEXT("TXT"),
    CODE("SRC"),
    IMAGE("IMG"),
    VIDEO("VID"),
    AUDIO("AUD"),
    ARCHIVE("ZIP"),
    PDF("PDF"),
    DOC("DOC"),
    APK("APK"),
    UNKNOWN("?");

    public final String shortLabel;

    FileCategory(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    /**
     * Maps a file name to its visual category by inspecting the extension. Does not consider
     * directory-ness; the caller decides whether to render a folder icon instead.
     *
     * @param name file name (may include or omit the extension)
     * @return matching category, or {@link #UNKNOWN} when the extension is missing/unrecognised
     */
    public static FileCategory ofExtension(String name) {
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
        if (endsWithAny(lower, CODE_EXT)) return CODE;
        if (endsWithAny(lower, TEXT_EXT)) return TEXT;
        if (endsWithAny(lower, DOC_EXT)) return DOC;
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
            ".zip", ".tar", ".gz", ".bz2", ".xz", ".7z", ".rar",
            ".tgz", ".tbz", ".txz", ".jar", ".war", ".cpio", ".ar", ".aar"
    };

    private static final String[] CODE_EXT = {
            ".java", ".kt", ".kts", ".scala", ".groovy",
            ".py", ".rb", ".php", ".pl", ".lua",
            ".c", ".h", ".cpp", ".hpp", ".cc", ".cxx", ".m", ".mm",
            ".rs", ".go", ".swift", ".dart", ".ex", ".exs",
            ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".vue", ".svelte",
            ".css", ".scss", ".sass", ".less",
            ".sh", ".bash", ".zsh", ".fish", ".ps1", ".bat", ".cmd",
            ".sql", ".r", ".jl", ".clj", ".hs", ".erl"
    };

    private static final String[] TEXT_EXT = {
            ".txt", ".md", ".markdown", ".rst", ".log", ".csv", ".tsv",
            ".json", ".xml", ".yaml", ".yml", ".toml", ".ini", ".conf", ".cfg", ".prop",
            ".properties", ".env", ".gradle", ".lock", ".html", ".htm"
    };

    private static final String[] DOC_EXT = {
            ".doc", ".docx", ".odt", ".rtf",
            ".xls", ".xlsx", ".ods", ".csv",
            ".ppt", ".pptx", ".odp",
            ".epub", ".mobi"
    };
}

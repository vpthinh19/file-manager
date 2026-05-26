package com.vpt.filemanager.core.format;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Single extension-based routing policy for files opened from the browser. */
@Singleton
public final class ExtensionRegistry {
    public enum Kind {
        TEXT,
        IMAGE,
        AUDIO,
        VIDEO,
        ARCHIVE,
        APK_INSTALLER,
        EXTERNAL,
        OPEN_AS
    }

    private static final Set<String> TEXT = Set.of(
            ".txt", ".text", ".md", ".markdown", ".log", ".csv", ".tsv", ".json", ".json5",
            ".xml", ".yaml", ".yml", ".toml", ".ini", ".conf", ".config", ".properties",
            ".prop", ".gradle", ".pro", ".sql", ".svg", ".html", ".htm", ".css", ".scss",
            ".sass", ".less", ".java", ".kt", ".kts", ".groovy", ".c", ".cc", ".cpp", ".cxx",
            ".h", ".hpp", ".m", ".mm", ".cs", ".go", ".rs", ".swift", ".dart", ".py", ".pyw",
            ".rb", ".php", ".pl", ".lua", ".r", ".sh", ".bash", ".zsh", ".fish", ".ps1",
            ".bat", ".cmd", ".js", ".jsx", ".mjs", ".cjs", ".ts", ".tsx", ".vue", ".svelte",
            ".smali", ".asm", ".diff", ".patch", ".gitignore", ".gitattributes", ".editorconfig",
            ".dockerignore", ".env", ".bashrc", ".zshrc"
    );
    private static final Set<String> IMAGE = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".heic", ".heif", ".avif"
    );
    private static final Set<String> AUDIO = Set.of(
            ".mp3", ".wav", ".ogg", ".oga", ".flac", ".aac", ".m4a", ".opus", ".amr"
    );
    private static final Set<String> VIDEO = Set.of(
            ".mp4", ".m4v", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".ts"
    );
    private static final Set<String> ARCHIVE = Set.of(
            ".zip", ".jar", ".war", ".aar", ".7z", ".rar", ".rar5", ".tar", ".tar.gz",
            ".tgz", ".tar.bz2", ".tbz", ".tbz2", ".tar.xz", ".txz", ".tar.zst", ".tzst",
            ".gz", ".bz2", ".xz", ".zst", ".cpio", ".xar", ".ar", ".cab", ".iso", ".lha",
            ".lzh", ".warc"
    );
    private static final Set<String> EXTERNAL_DOCUMENT = Set.of(
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".odt", ".ods", ".odp",
            ".pdf", ".epub"
    );
    private static final Set<String> SPECIAL_TEXT_NAMES = Set.of(
            ".env", ".bashrc", ".zshrc", ".gitignore", ".gitattributes", ".editorconfig",
            ".dockerignore"
    );

    @Inject
    public ExtensionRegistry() {
    }

    @NonNull
    public Kind classify(@NonNull String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (SPECIAL_TEXT_NAMES.contains(lower)) return Kind.TEXT;
        if (!hasExtension(lower)) return Kind.OPEN_AS;
        if (lower.endsWith(".apk")) return Kind.APK_INSTALLER;
        if (matches(lower, ARCHIVE)) return Kind.ARCHIVE;
        if (matches(lower, EXTERNAL_DOCUMENT)) return Kind.EXTERNAL;
        if (matches(lower, TEXT)) return Kind.TEXT;
        if (matches(lower, IMAGE)) return Kind.IMAGE;
        if (matches(lower, AUDIO)) return Kind.AUDIO;
        if (matches(lower, VIDEO)) return Kind.VIDEO;
        return Kind.EXTERNAL;
    }

    private static boolean hasExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 || dot == 0 && name.indexOf('.', 1) >= 0;
    }

    private static boolean matches(String value, Set<String> extensions) {
        for (String extension : extensions) {
            if (value.endsWith(extension)) return true;
        }
        return false;
    }
}

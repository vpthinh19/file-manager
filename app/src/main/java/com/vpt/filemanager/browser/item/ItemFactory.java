package com.vpt.filemanager.browser.item;

import androidx.annotation.NonNull;

import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;
import com.vpt.filemanager.data.archive.ArchiveFormat;

/** Classifies local paths once while producing items, keeping row binding cheap. */
@Singleton
public final class ItemFactory {
    @Inject
    public ItemFactory() {
    }

    public Item local(String path, String name, boolean folder, long size, long modifiedAt) {
        return Item.local(path, name, folder, size, modifiedAt,
                folder ? ItemType.FOLDER : behavior(name));
    }

    public Item bookmark(String path, String name, boolean folder, long size, long modifiedAt) {
        return Item.bookmark(path, name, folder, size, modifiedAt,
                folder ? ItemType.FOLDER : behavior(name));
    }

    public Item archive(Path path, String name, boolean folder, long size, long modifiedAt) {
        return Item.archive(path, name, folder, size, modifiedAt,
                folder ? ItemType.FOLDER : behavior(name));
    }

    @NonNull
    private static ItemType behavior(String name) {
        String suffix = extension(name);
        if (matches(suffix, "txt", "md", "json", "xml", "html", "css", "java", "kt",
                "kts", "c", "cpp", "h", "py", "js", "ts", "sh", "gradle", "properties",
                "yaml", "yml", "sql", "log")) return ItemType.TEXT;
        if (matches(suffix, "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")) return ItemType.IMAGE;
        if (matches(suffix, "mp4", "mkv", "webm", "avi", "mov")) return ItemType.VIDEO;
        if (matches(suffix, "mp3", "wav", "ogg", "m4a", "flac", "aac")) return ItemType.AUDIO;
        if (ArchiveFormat.isContainer(name)) return ItemType.ARCHIVE;
        return ItemType.EXTERNAL;
    }

    private static String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String actual, String... values) {
        for (String value : values) if (value.equals(actual)) return true;
        return false;
    }
}

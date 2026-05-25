package com.vpt.filemanager.content;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.storage.archive.ArchiveAccess;
import com.vpt.filemanager.storage.LocalStorageAdapter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Detects opened-file behavior from content; extension is deliberately not authoritative. */
@Singleton
public final class ContentDetector {
    private static final int SNIFF_BYTES = 512;
    private final LocalStorageAdapter storage;
    private final ArchiveAccess archives;

    @Inject
    public ContentDetector(LocalStorageAdapter storage, ArchiveAccess archives) {
        this.storage = storage;
        this.archives = archives;
    }

    public boolean isArchive(@NonNull File file) {
        return archives.canOpen(file);
    }

    @NonNull
    public ContentType detect(@NonNull File file) throws FileOperationException {
        byte[] bytes = new byte[SNIFF_BYTES];
        int count;
        try (InputStream input = storage.openRead(file)) {
            count = Math.max(input.read(bytes), 0);
        } catch (IOException error) {
            throw new FileOperationException("Cannot inspect: " + file.getName(), error);
        }
        if (png(bytes, count) || jpeg(bytes, count) || gif(bytes, count) || webp(bytes, count)) {
            return ContentType.IMAGE;
        }
        if (mp4(bytes, count)) return ContentType.VIDEO;
        if (mp3(bytes, count) || wav(bytes, count) || ogg(bytes, count) || flac(bytes, count)) {
            return ContentType.AUDIO;
        }
        if (text(bytes, count)) return ContentType.TEXT;
        return ContentType.EXTERNAL;
    }

    private static boolean png(byte[] b, int n) {
        return n >= 8 && (b[0] & 0xff) == 0x89 && ascii(b, 1, "PNG");
    }

    private static boolean jpeg(byte[] b, int n) {
        return n >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8
                && (b[2] & 0xff) == 0xff;
    }

    private static boolean gif(byte[] b, int n) {
        return n >= 6 && (ascii(b, 0, "GIF87a") || ascii(b, 0, "GIF89a"));
    }

    private static boolean webp(byte[] b, int n) {
        return n >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP");
    }

    private static boolean mp4(byte[] b, int n) {
        return n >= 12 && ascii(b, 4, "ftyp");
    }

    private static boolean mp3(byte[] b, int n) {
        return n >= 3 && ascii(b, 0, "ID3")
                || n >= 2 && (b[0] & 0xff) == 0xff && (b[1] & 0xe0) == 0xe0;
    }

    private static boolean wav(byte[] b, int n) {
        return n >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WAVE");
    }

    private static boolean ogg(byte[] b, int n) {
        return n >= 4 && ascii(b, 0, "OggS");
    }

    private static boolean flac(byte[] b, int n) {
        return n >= 4 && ascii(b, 0, "fLaC");
    }

    private static boolean text(byte[] bytes, int count) {
        if (count == 0) return true;
        if (count >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) return true;
        for (int i = 0; i < count; i++) if (bytes[i] == 0) return false;
        String decoded = new String(bytes, 0, count, StandardCharsets.UTF_8);
        long controls = decoded.chars().filter(value -> value < 0x09
                || value > 0x0d && value < 0x20).count();
        return controls <= Math.max(1, decoded.length() / 20);
    }

    private static boolean ascii(byte[] bytes, int start, String value) {
        if (bytes.length < start + value.length()) return false;
        for (int index = 0; index < value.length(); index++) {
            if (bytes[start + index] != (byte) value.charAt(index)) return false;
        }
        return true;
    }
}

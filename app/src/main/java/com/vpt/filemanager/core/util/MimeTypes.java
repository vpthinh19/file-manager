package com.vpt.filemanager.core.util;

import java.net.URLConnection;
import java.util.Locale;

import com.vpt.filemanager.domain.model.MimeCategory;

public final class MimeTypes {
    private MimeTypes() {
    }

    public static String detect(String name) {
        String mime = URLConnection.guessContentTypeFromName(name);
        return mime == null ? "application/octet-stream" : mime;
    }

    public static MimeCategory category(String mimeType, String name) {
        String mime = mimeType == null ? detect(name) : mimeType;
        if (mime.startsWith("image/")) {
            return MimeCategory.IMAGE;
        }
        if (mime.startsWith("video/")) {
            return MimeCategory.VIDEO;
        }
        if (mime.startsWith("audio/")) {
            return MimeCategory.AUDIO;
        }
        if (mime.startsWith("text/")) {
            return MimeCategory.TEXT;
        }
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")
                || lower.endsWith(".bz2") || lower.endsWith(".xz") || lower.endsWith(".7z")) {
            return MimeCategory.ARCHIVE;
        }
        return MimeCategory.OTHER;
    }
}


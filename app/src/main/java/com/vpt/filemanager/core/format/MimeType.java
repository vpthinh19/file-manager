package com.vpt.filemanager.core.format;

import java.net.URLConnection;

/** MIME hint for system Intents, via {@link URLConnection#guessContentTypeFromName(String)}. */
public final class MimeType {
    private MimeType() {
    }

    public static String detect(String name) {
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        String mime = URLConnection.guessContentTypeFromName(name);
        return mime == null ? "application/octet-stream" : mime;
    }
}

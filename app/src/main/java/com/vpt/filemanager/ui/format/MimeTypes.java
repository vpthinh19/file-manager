package com.vpt.filemanager.ui.format;

import java.net.URLConnection;

/**
 * Thin wrapper over {@link URLConnection#guessContentTypeFromName(String)}. The result is used
 * only as the MIME hint for system Intents (ACTION_VIEW, ACTION_SEND).
 */
public final class MimeTypes {
    private MimeTypes() {
    }

    public static String detect(String name) {
        String mime = URLConnection.guessContentTypeFromName(name);
        return mime == null ? "application/octet-stream" : mime;
    }
}

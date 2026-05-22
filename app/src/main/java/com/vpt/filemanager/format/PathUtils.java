package com.vpt.filemanager.format;

public final class PathUtils {
    private PathUtils() {
    }

    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.replace('\\', '/').replaceAll("/{2,}", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}


package com.vpt.filemanager.domain.model;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.vpt.filemanager.core.PathUtils;

public final class FilePath {
    public static final String SCHEME_FILE = "file";
    public static final String SCHEME_ARCHIVE = "archive";
    public static final String SCHEME_TRASH = "trash";
    public static final String SCHEME_BOOKMARK = "bookmark";

    /** Virtual roots cho drawer items — không có authority hay path "thật". */
    public static final FilePath TRASH_ROOT = new FilePath(SCHEME_TRASH, "", "/");
    public static final FilePath BOOKMARK_ROOT = new FilePath(SCHEME_BOOKMARK, "", "/");

    private final String scheme;
    private final String authority;
    private final String path;

    public FilePath(String scheme, String authority, String path) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.authority = authority == null ? "" : authority;
        this.path = PathUtils.normalize(path);
    }

    public static FilePath local(String absolutePath) {
        return new FilePath(SCHEME_FILE, "", absolutePath);
    }

    public static FilePath inArchive(FilePath archivePath, String innerPath) {
        return new FilePath(SCHEME_ARCHIVE, archivePath.toString(), innerPath);
    }

    public static FilePath parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Path is blank");
        }
        if (raw.startsWith("file://")) {
            return local(decode(raw.substring("file://".length())));
        }
        if (raw.startsWith("archive://")) {
            String rest = raw.substring("archive://".length());
            int slash = rest.indexOf('/');
            if (slash < 0) {
                return new FilePath(SCHEME_ARCHIVE, decode(rest), "/");
            }
            return new FilePath(SCHEME_ARCHIVE, decode(rest.substring(0, slash)), "/" + decode(rest.substring(slash + 1)));
        }
        return local(raw);
    }

    public boolean isLocal() {
        return SCHEME_FILE.equals(scheme);
    }

    public boolean isArchive() {
        return SCHEME_ARCHIVE.equals(scheme);
    }

    public boolean isTrash() {
        return SCHEME_TRASH.equals(scheme);
    }

    public boolean isBookmark() {
        return SCHEME_BOOKMARK.equals(scheme);
    }

    public FilePath parent() {
        if ("/".equals(path)) {
            return this;
        }
        int index = path.lastIndexOf('/');
        return new FilePath(scheme, authority, index <= 0 ? "/" : path.substring(0, index));
    }

    public FilePath child(String name) {
        String cleanName = Objects.requireNonNull(name, "name").replace("\\", "/");
        if (cleanName.contains("/")) {
            throw new IllegalArgumentException("Child name must not contain path separators");
        }
        return new FilePath(scheme, authority, "/".equals(path) ? "/" + cleanName : path + "/" + cleanName);
    }

    public String name() {
        if ("/".equals(path)) {
            return "/";
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public String extension() {
        String name = name();
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
    }

    public String scheme() {
        return scheme;
    }

    public String authority() {
        return authority;
    }

    public String path() {
        return path;
    }

    @Override
    public String toString() {
        if (isLocal()) {
            return "file://" + encode(path);
        }
        return scheme + "://" + encode(authority) + "/" + encode(path.substring(1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FilePath)) {
            return false;
        }
        FilePath filePath = (FilePath) o;
        return scheme.equals(filePath.scheme) && authority.equals(filePath.authority) && path.equals(filePath.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, authority, path);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

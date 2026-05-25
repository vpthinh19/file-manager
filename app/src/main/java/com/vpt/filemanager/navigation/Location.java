package com.vpt.filemanager.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable virtual address shown by one pane. It never stores a physical filesystem path. */
public final class Location {
    public enum Scheme {
        STORAGE,
        TRASH,
        BOOKMARKS,
        SEARCH
    }

    private final Scheme scheme;
    @Nullable private final String storagePath;
    @Nullable private final String archiveInnerPath;
    @Nullable private final String query;

    private Location(Scheme scheme, @Nullable String storagePath,
                     @Nullable String archiveInnerPath, @Nullable String query) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.storagePath = storagePath;
        this.archiveInnerPath = archiveInnerPath;
        this.query = query;
    }

    @NonNull
    public static Location storageRoot() {
        return storage("");
    }

    @NonNull
    public static Location storage(@NonNull String virtualPath) {
        return new Location(Scheme.STORAGE, normalizeStoragePath(virtualPath), null, null);
    }

    @NonNull
    public static Location archive(@NonNull String containerPath, @NonNull String innerPath) {
        return new Location(Scheme.STORAGE, normalizeStoragePath(containerPath),
                normalizeInnerPath(innerPath), null);
    }

    @NonNull
    public static Location trash() {
        return new Location(Scheme.TRASH, null, null, null);
    }

    @NonNull
    public static Location bookmarks() {
        return new Location(Scheme.BOOKMARKS, null, null, null);
    }

    @NonNull
    public static Location search(@NonNull String scopePath, @NonNull String query) {
        return new Location(Scheme.SEARCH, normalizeStoragePath(scopePath), null,
                Objects.requireNonNull(query, "query"));
    }

    public Scheme scheme() {
        return scheme;
    }

    public boolean isStorage() {
        return scheme == Scheme.STORAGE;
    }

    public boolean isTrash() {
        return scheme == Scheme.TRASH;
    }

    public boolean isBookmarks() {
        return scheme == Scheme.BOOKMARKS;
    }

    public boolean isSearch() {
        return scheme == Scheme.SEARCH;
    }

    public boolean isArchiveEntry() {
        return archiveInnerPath != null;
    }

    public boolean isStorageRoot() {
        return isStorage() && !isArchiveEntry() && storagePath().isEmpty();
    }

    /** Virtual storage path, empty for `storage:` and `/...` below that root. */
    @NonNull
    public String storagePath() {
        if (storagePath == null) {
            throw new IllegalStateException("Location has no storage path: " + scheme);
        }
        return storagePath;
    }

    @NonNull
    public String archiveInnerPath() {
        if (archiveInnerPath == null) {
            throw new IllegalStateException("Location is not mounted archive content");
        }
        return archiveInnerPath;
    }

    @NonNull
    public String query() {
        if (query == null) {
            throw new IllegalStateException("Location is not a search");
        }
        return query;
    }

    @Nullable
    public Location parent() {
        if (isArchiveEntry()) {
            if ("/".equals(archiveInnerPath())) {
                return storage(parentPath(storagePath()));
            }
            return archive(storagePath(), parentPath(archiveInnerPath()));
        }
        if (!isStorage() || isStorageRoot()) return null;
        return storage(parentPath(storagePath()));
    }

    @NonNull
    public Location child(@NonNull String name) {
        String child = cleanName(name);
        if (isArchiveEntry()) {
            String prefix = "/".equals(archiveInnerPath()) ? "" : archiveInnerPath();
            return archive(storagePath(), prefix + "/" + child);
        }
        if (!isStorage()) {
            throw new IllegalStateException("Only storage containers have direct children");
        }
        return storage(storagePath() + "/" + child);
    }

    @NonNull
    public String serialize() {
        if (isTrash()) return "trash:";
        if (isBookmarks()) return "bookmarks:";
        if (isSearch()) {
            return "search:?scope=" + encode(storagePath()) + "&query=" + encode(query());
        }
        if (isArchiveEntry()) return "storage:" + storagePath() + "!" + archiveInnerPath();
        return "storage:" + storagePath();
    }

    @NonNull
    public static Location parse(@NonNull String serialized) {
        if ("trash:".equals(serialized) || "trash".equals(serialized)) return trash();
        if ("bookmarks:".equals(serialized) || "bookmarks".equals(serialized)) return bookmarks();
        if (serialized.startsWith("search:?scope=")) {
            int queryStart = serialized.indexOf("&query=");
            if (queryStart < 0) throw new IllegalArgumentException("Malformed search location");
            String scope = serialized.substring("search:?scope=".length(), queryStart);
            return search(decode(scope), decode(serialized.substring(queryStart + 7)));
        }
        String raw = serialized.startsWith("storage:") ? serialized.substring(8) : serialized;
        int mounted = raw.indexOf("!/");
        return mounted < 0 ? storage(raw)
                : archive(raw.substring(0, mounted), raw.substring(mounted + 1));
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Location other)) return false;
        return scheme == other.scheme
                && Objects.equals(storagePath, other.storagePath)
                && Objects.equals(archiveInnerPath, other.archiveInnerPath)
                && Objects.equals(query, other.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, storagePath, archiveInnerPath, query);
    }

    @Override
    public String toString() {
        return serialize();
    }

    private static String normalizeStoragePath(String raw) {
        String value = Objects.requireNonNull(raw, "storagePath").replace('\\', '/').trim();
        if (value.startsWith("storage:")) value = value.substring(8);
        if (value.isEmpty() || "/".equals(value)) return "";
        if (!value.startsWith("/")) value = "/" + value;
        while (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        for (String part : value.substring(1).split("/")) {
            if (".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("Storage path cannot traverse parent");
            }
        }
        return value;
    }

    private static String normalizeInnerPath(String raw) {
        String value = Objects.requireNonNull(raw, "innerPath").replace('\\', '/');
        if (!value.startsWith("/")) value = "/" + value;
        while (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String cleanName(String name) {
        String value = Objects.requireNonNull(name, "name");
        if (value.isBlank() || value.contains("/") || value.contains("\\")
                || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("Invalid child name");
        }
        return value;
    }

    private static String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "" : path.substring(0, slash);
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}

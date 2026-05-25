package com.vpt.filemanager.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable address rendered by one pane. Archive content is mounted below storage. */
public final class Location {
    public static final String STORAGE_ROOT_PATH = "/storage/emulated/0";

    public enum Scheme {
        STORAGE,
        TRASH,
        BOOKMARKS,
        SEARCH
    }

    private final Scheme scheme;
    @Nullable private final String physicalPath;
    @Nullable private final String archiveInnerPath;
    @Nullable private final String query;

    private Location(Scheme scheme, @Nullable String physicalPath,
                     @Nullable String archiveInnerPath, @Nullable String query) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.physicalPath = physicalPath;
        this.archiveInnerPath = archiveInnerPath;
        this.query = query;
    }

    @NonNull
    public static Location storageRoot() {
        return storage(STORAGE_ROOT_PATH);
    }

    @NonNull
    public static Location storage(@NonNull String path) {
        return new Location(Scheme.STORAGE, normalizePhysical(path), null, null);
    }

    @NonNull
    public static Location archive(@NonNull String container, @NonNull String innerPath) {
        return new Location(Scheme.STORAGE, normalizePhysical(container),
                normalizeInner(innerPath), null);
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
    public static Location search(@NonNull String scope, @NonNull String query) {
        return new Location(Scheme.SEARCH, normalizePhysical(scope), null,
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
        return isStorage() && !isArchiveEntry() && STORAGE_ROOT_PATH.equals(physicalPath());
    }

    @NonNull
    public String physicalPath() {
        if (physicalPath == null) {
            throw new IllegalStateException("Location has no physical path: " + scheme);
        }
        return physicalPath;
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
                return storage(parentPhysical(physicalPath()));
            }
            return archive(physicalPath(), parentInner(archiveInnerPath()));
        }
        if (!isStorage() || isStorageRoot()) {
            return null;
        }
        String parent = parentPhysical(physicalPath());
        if (!withinStorage(parent)) {
            return storageRoot();
        }
        return storage(parent);
    }

    @NonNull
    public Location child(@NonNull String name) {
        if (isArchiveEntry()) {
            String prefix = "/".equals(archiveInnerPath()) ? "" : archiveInnerPath();
            return archive(physicalPath(), prefix + "/" + name);
        }
        if (!isStorage()) {
            throw new IllegalStateException("Only storage containers have direct children");
        }
        return storage(physicalPath() + "/" + name);
    }

    @NonNull
    public String serialize() {
        if (isTrash()) return "trash:";
        if (isBookmarks()) return "bookmarks:";
        if (isSearch()) {
            return "search:?scope=" + encode(virtualStoragePath()) + "&query=" + encode(query());
        }
        if (isArchiveEntry()) return "storage:" + virtualStoragePath() + "!" + archiveInnerPath();
        return "storage:" + virtualStoragePath();
    }

    @NonNull
    public static Location parse(@NonNull String serialized) {
        if ("trash:".equals(serialized) || "trash".equals(serialized)) return trash();
        if ("bookmarks:".equals(serialized) || "bookmarks".equals(serialized)) return bookmarks();
        if (serialized.startsWith("search:?scope=")) {
            int queryStart = serialized.indexOf("&query=");
            if (queryStart < 0) throw new IllegalArgumentException("Malformed search location");
            String scope = serialized.substring("search:?scope=".length(), queryStart);
            return search(fromVirtualStoragePath(decode(scope)), decode(serialized.substring(queryStart + 7)));
        }
        String raw = serialized.startsWith("storage:") ? serialized.substring(8) : serialized;
        int mounted = raw.indexOf("!/");
        return mounted < 0 ? storage(fromVirtualStoragePath(raw))
                : archive(fromVirtualStoragePath(raw.substring(0, mounted)), raw.substring(mounted + 1));
    }

    public boolean observesChangeAt(@NonNull Location changed) {
        if (equals(changed)) return true;
        if (isArchiveEntry()) {
            return changed.isStorage() && !changed.isArchiveEntry()
                    && parentPhysical(physicalPath()).equals(changed.physicalPath());
        }
        if (isSearch() && changed.isStorage() && !changed.isArchiveEntry()) {
            return descendant(physicalPath(), changed.physicalPath())
                    || descendant(changed.physicalPath(), physicalPath());
        }
        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Location other)) return false;
        return scheme == other.scheme
                && Objects.equals(physicalPath, other.physicalPath)
                && Objects.equals(archiveInnerPath, other.archiveInnerPath)
                && Objects.equals(query, other.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, physicalPath, archiveInnerPath, query);
    }

    @Override
    public String toString() {
        return serialize();
    }

    private static String normalizePhysical(String raw) {
        String value = Objects.requireNonNull(raw, "path").replace('\\', '/');
        while (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value.isEmpty() ? "/" : value;
    }

    private static String normalizeInner(String raw) {
        String value = Objects.requireNonNull(raw, "innerPath").replace('\\', '/');
        if (!value.startsWith("/")) value = "/" + value;
        while (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String virtualStoragePath() {
        String path = physicalPath();
        if (path.equals(STORAGE_ROOT_PATH)) return "";
        if (path.startsWith(STORAGE_ROOT_PATH + "/")) return path.substring(STORAGE_ROOT_PATH.length());
        return path;
    }

    private static String fromVirtualStoragePath(String path) {
        if (path.isEmpty()) return STORAGE_ROOT_PATH;
        if (path.equals(STORAGE_ROOT_PATH) || path.startsWith(STORAGE_ROOT_PATH + "/")) return path;
        return path.startsWith("/") ? STORAGE_ROOT_PATH + path : STORAGE_ROOT_PATH + "/" + path;
    }

    private static String parentPhysical(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    private static String parentInner(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    private static boolean withinStorage(String path) {
        return path.equals(STORAGE_ROOT_PATH) || path.startsWith(STORAGE_ROOT_PATH + "/");
    }

    private static boolean descendant(String path, String ancestor) {
        return path.equals(ancestor) || path.startsWith(ancestor + "/");
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}

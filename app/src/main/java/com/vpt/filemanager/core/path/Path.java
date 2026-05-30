package com.vpt.filemanager.core.path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable virtual address shown by one pane. It never stores a physical filesystem location. */
public final class Path {
    public enum Scheme {
        STORAGE,
        TRASH,
        BOOKMARKS,
        SEARCH
    }

    private final Scheme scheme;
    @Nullable private final String storagePath;
    private final List<String> archivePaths;
    @Nullable private final String query;

    private Path(Scheme scheme, @Nullable String storagePath, @NonNull List<String> archivePaths,
                 @Nullable String query) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.storagePath = storagePath;
        this.archivePaths = List.copyOf(archivePaths);
        this.query = query;
    }

    @NonNull
    public static Path storageRoot() {
        return storage("");
    }

    @NonNull
    public static Path storage(@NonNull String virtualPath) {
        return new Path(Scheme.STORAGE, normalizeStoragePath(virtualPath), List.of(), null);
    }

    /** Creates a location within a top-level physical archive. */
    @NonNull
    public static Path archive(@NonNull String containerPath, @NonNull String innerPath) {
        return new Path(Scheme.STORAGE, normalizeStoragePath(containerPath),
                List.of(normalizeInnerPath(innerPath)), null);
    }

    @NonNull
    public static Path trash() {
        return new Path(Scheme.TRASH, null, List.of(), null);
    }

    @NonNull
    public static Path bookmarks() {
        return new Path(Scheme.BOOKMARKS, null, List.of(), null);
    }

    @NonNull
    public static Path search(@NonNull String scopePath, @NonNull String query) {
        return new Path(Scheme.SEARCH, normalizeStoragePath(scopePath), List.of(),
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

    public boolean isInsideArchive() {
        return !archivePaths.isEmpty();
    }

    public int archiveDepth() {
        return archivePaths.size();
    }

    @NonNull
    public List<String> archivePaths() {
        return archivePaths;
    }

    public boolean isStorageRoot() {
        return isStorage() && !isInsideArchive() && storagePath().isEmpty();
    }

    /** Physical storage location of the outermost container, or ordinary storage path. */
    @NonNull
    public String storagePath() {
        if (storagePath == null) {
            throw new IllegalStateException("Path has no storage component: " + scheme);
        }
        return storagePath;
    }

    /** Path within the innermost opened archive. */
    @NonNull
    public String archiveInnerPath() {
        if (archivePaths.isEmpty()) {
            throw new IllegalStateException("Path is not mounted archive content");
        }
        return archivePaths.get(archivePaths.size() - 1);
    }

    @NonNull
    public String query() {
        if (query == null) {
            throw new IllegalStateException("Path is not a search");
        }
        return query;
    }

    /** Opens this file as the root of an archive, retaining parent archive boundaries. */
    @NonNull
    public Path mountArchive() {
        if (!isStorage()) {
            throw new IllegalStateException("Only storage entries can be opened as archives");
        }
        if (!isInsideArchive()) return archive(storagePath(), "/");
        List<String> nested = new ArrayList<>(archivePaths);
        nested.add("/");
        return new Path(Scheme.STORAGE, storagePath(), nested, null);
    }

    @Nullable
    public Path parent() {
        if (isInsideArchive()) {
            String inner = archiveInnerPath();
            if (!"/".equals(inner)) {
                List<String> parent = new ArrayList<>(archivePaths);
                parent.set(parent.size() - 1, normalizeInnerPath(parentPath(inner)));
                return new Path(Scheme.STORAGE, storagePath(), parent, null);
            }
            if (archivePaths.size() == 1) return storage(parentPath(storagePath()));
            List<String> parent = new ArrayList<>(archivePaths);
            parent.remove(parent.size() - 1);
            String mountedEntry = parent.remove(parent.size() - 1);
            parent.add(normalizeInnerPath(parentPath(mountedEntry)));
            return new Path(Scheme.STORAGE, storagePath(), parent, null);
        }
        if (!isStorage() || isStorageRoot()) return null;
        return storage(parentPath(storagePath()));
    }

    @NonNull
    public Path child(@NonNull String name) {
        String child = cleanName(name);
        if (isInsideArchive()) {
            List<String> result = new ArrayList<>(archivePaths);
            String current = archiveInnerPath();
            String prefix = "/".equals(current) ? "" : current;
            result.set(result.size() - 1, normalizeInnerPath(prefix + "/" + child));
            return new Path(Scheme.STORAGE, storagePath(), result, null);
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
        StringBuilder serialized = new StringBuilder("storage:").append(storagePath());
        for (String inner : archivePaths) serialized.append('!').append(inner);
        return serialized.toString();
    }

    @NonNull
    public static Path parse(@NonNull String serialized) {
        if ("trash:".equals(serialized) || "trash".equals(serialized)) return trash();
        if ("bookmarks:".equals(serialized) || "bookmarks".equals(serialized)) return bookmarks();
        if (serialized.startsWith("search:?scope=")) {
            int queryStart = serialized.indexOf("&query=");
            if (queryStart < 0) throw new IllegalArgumentException("Malformed search path");
            String scope = serialized.substring("search:?scope=".length(), queryStart);
            return search(decode(scope), decode(serialized.substring(queryStart + 7)));
        }
        String raw = serialized.startsWith("storage:") ? serialized.substring(8) : serialized;
        int mounted = raw.indexOf("!/");
        if (mounted < 0) return storage(raw);
        String physical = raw.substring(0, mounted);
        String remainder = raw.substring(mounted);
        List<String> inner = new ArrayList<>();
        for (String part : remainder.split("!", -1)) {
            if (!part.isEmpty()) inner.add(normalizeInnerPath(part));
        }
        return new Path(Scheme.STORAGE, normalizeStoragePath(physical), inner, null);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Path other)) return false;
        return scheme == other.scheme
                && Objects.equals(storagePath, other.storagePath)
                && archivePaths.equals(other.archivePaths)
                && Objects.equals(query, other.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, storagePath, archivePaths, query);
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
        for (String part : value.substring(1).split("/")) {
            if (".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("Archive path cannot traverse parent");
            }
        }
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

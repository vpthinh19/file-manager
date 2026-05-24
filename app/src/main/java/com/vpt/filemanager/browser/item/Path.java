package com.vpt.filemanager.browser.item;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Address currently rendered by a pane. It never represents a retained virtual tree. */
public final class Path {
    public enum Kind {
        STORAGE,
        ARCHIVE,
        TRASH,
        BOOKMARKS,
        SEARCH
    }

    private final Kind kind;
    @Nullable private final String directory;
    @Nullable private final String query;
    @Nullable private final String container;

    private Path(Kind kind, @Nullable String directory, @Nullable String query,
                 @Nullable String container) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.directory = directory;
        this.query = query;
        this.container = container;
    }

    @NonNull
    public static Path storage(@NonNull String directory) {
        return new Path(Kind.STORAGE, normalize(directory), null, null);
    }

    @NonNull
    public static Path archive(@NonNull String container, @NonNull String directory) {
        return new Path(Kind.ARCHIVE, normalizeArchive(directory), null, normalize(container));
    }

    @NonNull
    public static Path trash() {
        return new Path(Kind.TRASH, null, null, null);
    }

    @NonNull
    public static Path bookmarks() {
        return new Path(Kind.BOOKMARKS, null, null, null);
    }

    @NonNull
    public static Path search(@NonNull String scope, @NonNull String query) {
        return new Path(Kind.SEARCH, normalize(scope), Objects.requireNonNull(query, "query"), null);
    }

    @NonNull public Kind kind() { return kind; }
    public boolean isStorage() { return kind == Kind.STORAGE; }
    public boolean isArchive() { return kind == Kind.ARCHIVE; }
    public boolean isTrash() { return kind == Kind.TRASH; }
    public boolean isBookmarks() { return kind == Kind.BOOKMARKS; }
    public boolean isSearch() { return kind == Kind.SEARCH; }

    @NonNull
    public String directory() {
        if (directory == null) throw new IllegalStateException("Path has no directory: " + kind);
        return directory;
    }

    @NonNull
    public String query() {
        if (query == null) throw new IllegalStateException("Path has no query: " + kind);
        return query;
    }

    @NonNull
    public String container() {
        if (container == null) throw new IllegalStateException("Path has no archive container: " + kind);
        return container;
    }

    @Nullable
    public Path parent() {
        if (isArchive()) {
            if ("/".equals(directory())) {
                int slash = container().lastIndexOf('/');
                return storage(slash <= 0 ? "/" : container().substring(0, slash));
            }
            int slash = directory().lastIndexOf('/');
            return archive(container(), slash <= 0 ? "/" : directory().substring(0, slash));
        }
        if (!isStorage()) return null;
        int slash = directory().lastIndexOf('/');
        return storage(slash <= 0 ? "/" : directory().substring(0, slash));
    }

    @NonNull
    public String serialize() {
        return switch (kind) {
            case STORAGE -> "storage:" + encode(directory());
            case ARCHIVE -> "archive:" + encode(container()) + ":" + encode(directory());
            case TRASH -> "trash";
            case BOOKMARKS -> "bookmarks";
            case SEARCH -> "search:" + encode(directory()) + ":" + encode(query());
        };
    }

    @NonNull
    public static Path deserialize(@NonNull String raw) {
        if (raw.equals("trash")) return trash();
        if (raw.equals("bookmarks")) return bookmarks();
        if (raw.startsWith("storage:")) return storage(decode(raw.substring(8)));
        if (raw.startsWith("archive:")) {
            String value = raw.substring(8);
            int separator = value.indexOf(':');
            if (separator < 1) throw new IllegalArgumentException("Malformed archive path");
            return archive(decode(value.substring(0, separator)), decode(value.substring(separator + 1)));
        }
        if (raw.startsWith("search:")) {
            String value = raw.substring(7);
            int separator = value.indexOf(':');
            if (separator < 1) throw new IllegalArgumentException("Malformed search path");
            return search(decode(value.substring(0, separator)), decode(value.substring(separator + 1)));
        }
        return storage(raw);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Path path)) return false;
        return kind == path.kind
                && Objects.equals(directory, path.directory)
                && Objects.equals(query, path.query)
                && Objects.equals(container, path.container);
    }

    @Override public int hashCode() { return Objects.hash(kind, directory, query, container); }
    @Override public String toString() { return serialize(); }

    private static String normalize(String raw) {
        String value = Objects.requireNonNull(raw, "directory").replace('\\', '/');
        while (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value.isEmpty() ? "/" : value;
    }

    private static String normalizeArchive(String raw) {
        String value = Objects.requireNonNull(raw, "directory").replace('\\', '/');
        if (!value.startsWith("/")) value = "/" + value;
        while (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}

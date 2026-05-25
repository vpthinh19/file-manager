package com.vpt.filemanager.storage.archive;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import me.zhanghai.android.libarchive.Archive;
import me.zhanghai.android.libarchive.ArchiveEntry;
import me.zhanghai.android.libarchive.ArchiveException;

import com.vpt.filemanager.entry.Entry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.error.ArchiveOperationException;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.error.NameConflictException;
import com.vpt.filemanager.storage.LocalStorageAdapter;

/**
 * Streaming libarchive gateway. Mutations build a validated replacement before touching the
 * original container; it does not retain an archive tree between pane refreshes.
 */
@Singleton
public final class ArchiveAccess {
    private static final int BUFFER_BYTES = 64 * 1024;

    private final java.nio.file.Path extractionDirectory;
    private final LocalStorageAdapter storage;

    @Inject
    public ArchiveAccess(@ApplicationContext Context context, LocalStorageAdapter storage) {
        this.extractionDirectory = context.getCacheDir().toPath().resolve("archive-preview");
        this.storage = storage;
    }

    @NonNull
    public List<Entry> list(@NonNull Path location) throws FileOperationException {
        requireArchive(location);
        String prefix = prefix(location);
        Map<String, ListedEntry> children = new LinkedHashMap<>();
        long archive = 0L;
        try {
            archive = openReader(containerPath(location));
            long entry;
            while ((entry = Archive.readNextHeader(archive)) != 0L) {
                String pathname = normalized(ArchiveEntry.pathnameUtf8(entry));
                if (!pathname.isEmpty() && pathname.startsWith(prefix)) {
                    String remaining = pathname.substring(prefix.length());
                    if (!remaining.isEmpty()) {
                        int separator = remaining.indexOf('/');
                        String name = separator < 0 ? remaining : remaining.substring(0, separator);
                        boolean folder = separator >= 0 || ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFDIR;
                        ListedEntry found = new ListedEntry(name, folder,
                                folder ? -1L : ArchiveEntry.size(entry),
                                ArchiveEntry.mtime(entry) * 1000L);
                        ListedEntry previous = children.get(name);
                        if (previous == null || (found.folder && !previous.folder)) {
                            children.put(name, found);
                        }
                    }
                }
                Archive.readDataSkip(archive);
            }
        } catch (ArchiveException error) {
            throw failure("Cannot read archive: " + location.serialize(), error);
        } finally {
            freeQuietly(archive);
        }
        List<Entry> result = new ArrayList<>(children.size());
        for (ListedEntry entry : children.values()) {
            String child = child(location.archiveInnerPath(), entry.name);
            result.add(Entry.archive(Path.archive(location.storagePath(), child), entry.name,
                    entry.folder, entry.size, entry.modifiedAt));
        }
        return result;
    }

    /** Uses libarchive's format bidding against file contents, not the file suffix. */
    public boolean canOpen(@NonNull java.io.File file) {
        if (!file.isFile()) return false;
        long archive = 0L;
        try {
            archive = openReader(file.getAbsolutePath());
            Archive.readNextHeader(archive);
            int format = Archive.format(archive) & Archive.FORMAT_BASE_MASK;
            return format != Archive.FORMAT_RAW && format != Archive.FORMAT_EMPTY;
        } catch (ArchiveException error) {
            return false;
        } finally {
            freeQuietly(archive);
        }
    }

    public boolean isDirectory(@NonNull Path location) throws FileOperationException {
        requireArchive(location);
        if ("/".equals(location.archiveInnerPath())) return true;
        String wanted = entryName(location.archiveInnerPath());
        long archive = 0L;
        try {
            archive = openReader(containerPath(location));
            long entry;
            while ((entry = Archive.readNextHeader(archive)) != 0L) {
                String candidate = entryName(ArchiveEntry.pathnameUtf8(entry));
                if (candidate.equals(wanted)) {
                    return ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFDIR;
                }
                if (candidate.startsWith(wanted + "/")) return true;
                Archive.readDataSkip(archive);
            }
            throw new ArchiveOperationException("Archive entry no longer exists");
        } catch (ArchiveException error) {
            throw failure("Cannot inspect archive entry", error);
        } finally {
            freeQuietly(archive);
        }
    }

    public boolean exists(@NonNull Path directory, @NonNull String name) throws FileOperationException {
        for (Entry child : list(directory)) {
            if (child.name().equals(name)) return true;
        }
        return false;
    }

    public boolean canWrite(@NonNull Path location) {
        if (!location.isInsideArchive() || !ArchiveFormat.isWritable(location.storagePath())) return false;
        long archive = 0L;
        try {
            archive = openReader(containerPath(location));
            Archive.readNextHeader(archive);
            int format = Archive.format(archive) & Archive.FORMAT_BASE_MASK;
            return format != Archive.FORMAT_RAR && format != Archive.FORMAT_RAR_V5
                    && format != Archive.FORMAT_RAW && format != Archive.FORMAT_EMPTY;
        } catch (ArchiveException error) {
            return false;
        } finally {
            freeQuietly(archive);
        }
    }

    public void create(@NonNull Path directory, @NonNull String name, boolean folder)
            throws FileOperationException {
        requireWritable(directory);
        String pathname = entryName(child(directory.archiveInnerPath(), name));
        if (exists(directory, name)) throw new NameConflictException(name);
        rewrite(containerPath(directory), original -> original,
                output -> writeEmptyEntry(output, new NewEntry(pathname, folder)));
    }

    public void rename(@NonNull Entry item, @NonNull String newName) throws FileOperationException {
        Path source = requireEntry(item);
        requireWritable(source);
        String oldName = entryName(source.archiveInnerPath());
        String parent = parentInner(source.archiveInnerPath());
        String replacement = entryName(child(parent, newName));
        if (oldName.equals(replacement)) return;
        if (exists(Path.archive(source.storagePath(), parent), newName)) {
            throw new NameConflictException(newName);
        }
        rewrite(containerPath(source), original -> {
            if (original.equals(oldName)) return replacement;
            if (original.startsWith(oldName + "/")) {
                return replacement + original.substring(oldName.length());
            }
            return original;
        }, null);
    }

    public void delete(@NonNull List<Entry> selected) throws FileOperationException {
        if (selected.isEmpty()) return;
        Path first = requireEntry(selected.get(0));
        requireWritable(first);
        List<String> removed = new ArrayList<>();
        for (Entry item : selected) {
            Path entry = requireEntry(item);
            if (!entry.storagePath().equals(first.storagePath())) {
                throw new ArchiveOperationException("Archive selection crosses containers");
            }
            removed.add(entryName(entry.archiveInnerPath()));
        }
        rewrite(containerPath(first), original -> {
            for (String path : removed) {
                if (original.equals(path) || original.startsWith(path + "/")) return null;
            }
            return original;
        }, null);
    }

    public void importFromStorage(@NonNull Path destination, @NonNull Entry source,
                                  @NonNull String name, boolean replace)
            throws FileOperationException {
        if (source.localPathOrNull() == null || source.isInsideArchive()) {
            throw new ArchiveOperationException("Only local items can be imported into an archive");
        }
        requireWritable(destination);
        importPath(destination, java.nio.file.Paths.get(source.localPath()), name, replace);
    }

    public void importFromArchive(@NonNull Path destination, @NonNull Entry source,
                                  @NonNull String name, boolean replace)
            throws FileOperationException {
        requireWritable(destination);
        requireEntry(source);
        java.nio.file.Path transferRoot = null;
        try {
            Files.createDirectories(extractionDirectory);
            transferRoot = Files.createTempDirectory(extractionDirectory, "transfer-");
            java.nio.file.Path extracted = transferRoot.resolve(safeFileName(source.name()));
            extractToStorage(source, extracted.toString());
            importPath(destination, extracted, name, replace);
        } catch (IOException error) {
            throw failure("Cannot prepare archive transfer: " + source.name(), error);
        } finally {
            deleteTemporaryTree(transferRoot);
        }
    }

    private void importPath(Path destination, java.nio.file.Path source, String name, boolean replace)
            throws FileOperationException {
        String importedRoot = entryName(child(destination.archiveInnerPath(), name));
        rewrite(containerPath(destination), original -> {
            if (replace && (original.equals(importedRoot)
                    || original.startsWith(importedRoot + "/"))) return null;
            return original;
        }, output -> appendStorage(output, source, importedRoot));
    }

    public void updateFromMaterialized(@NonNull Path target, @NonNull String materialized)
            throws FileOperationException {
        requireWritable(target);
        String entry = entryName(target.archiveInnerPath());
        java.nio.file.Path source = java.nio.file.Paths.get(materialized);
        rewrite(containerPath(target), original -> original.equals(entry) ? null : original,
                output -> appendStorage(output, source, entry));
    }

    public void extractToStorage(@NonNull Entry source, @NonNull String destination)
            throws FileOperationException {
        Path entryPath = requireEntry(source);
        String rootName = entryName(entryPath.archiveInnerPath());
        java.nio.file.Path target = java.nio.file.Paths.get(destination);
        long archive = 0L;
        boolean found = false;
        try {
            archive = openReader(containerPath(entryPath));
            long entry;
            while ((entry = Archive.readNextHeader(archive)) != 0L) {
                String pathname = entryName(ArchiveEntry.pathnameUtf8(entry));
                boolean matched = pathname.equals(rootName) || pathname.startsWith(rootName + "/");
                if (!matched) {
                    Archive.readDataSkip(archive);
                    continue;
                }
                found = true;
                String relative = pathname.equals(rootName) ? "" : pathname.substring(rootName.length() + 1);
                java.nio.file.Path output = relative.isEmpty() ? target : safeResolve(target, relative);
                boolean folder = ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFDIR
                        || (!relative.isEmpty() && source.isFolder() && pathname.endsWith("/"));
                if (source.isFolder() && relative.isEmpty()) folder = true;
                if (folder) {
                    Files.createDirectories(output);
                    Archive.readDataSkip(archive);
                } else {
                    java.nio.file.Path parent = output.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (OutputStream stream = Files.newOutputStream(output,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        copyToStream(archive, stream);
                    }
                }
            }
            if (!found) throw new ArchiveOperationException("Archive entry no longer exists: " + source.name());
        } catch (IOException error) {
            throw failure("Cannot extract archive entry: " + source.name(), error);
        } finally {
            freeQuietly(archive);
        }
    }

    @NonNull
    public String materialize(@NonNull Entry item) throws FileOperationException {
        Path target = requireEntry(item);
        if (item.isFolder()) throw new ArchiveOperationException("Cannot open an archive folder as a file");
        String wanted = entryName(target.archiveInnerPath());
        long archive = 0L;
        try {
            Files.createDirectories(extractionDirectory);
            java.nio.file.Path output = extractionDirectory.resolve(
                    Integer.toHexString(item.key().hashCode()) + "-" + safeFileName(item.name()));
            archive = openReader(containerPath(target));
            long entry;
            while ((entry = Archive.readNextHeader(archive)) != 0L) {
                if (normalized(ArchiveEntry.pathnameUtf8(entry)).equals(wanted)) {
                    try (OutputStream stream = Files.newOutputStream(output,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                        copyToStream(archive, stream);
                    }
                    return output.toString();
                }
                Archive.readDataSkip(archive);
            }
            throw new ArchiveOperationException("Archive entry no longer exists: " + item.name());
        } catch (IOException error) {
            throw failure("Cannot extract archive entry: " + item.name(), error);
        } finally {
            freeQuietly(archive);
        }
    }

    private void rewrite(String container, RenameTransform transform, ArchiveAppender addition)
            throws FileOperationException {
        java.nio.file.Path original = java.nio.file.Paths.get(container);
        java.nio.file.Path temp = null;
        long input = 0L;
        long output = 0L;
        boolean finished = false;
        try {
            java.nio.file.Path parent = original.getParent();
            temp = Files.createTempFile(parent, "." + original.getFileName(), ".rewrite");
            input = openReader(container);
            output = Archive.writeNew();
            configureWriter(output, container);
            Archive.writeOpenFileName(output, utf8(temp.toString()));
            long entry;
            while ((entry = Archive.readNextHeader(input)) != 0L) {
                String originalName = normalized(ArchiveEntry.pathnameUtf8(entry));
                String writtenName = transform.nameFor(originalName);
                if (writtenName == null) {
                    Archive.readDataSkip(input);
                    continue;
                }
                long copy = ArchiveEntry.clone(entry);
                try {
                    ArchiveEntry.setPathnameUtf8(copy, writtenName);
                    Archive.writeHeader(output, copy);
                    copyData(input, output);
                    Archive.writeFinishEntry(output);
                } finally {
                    ArchiveEntry.free(copy);
                }
            }
            if (addition != null) addition.append(output);
            Archive.writeClose(output);
            Archive.free(output);
            output = 0L;
            validate(temp.toString());
            replace(temp, original);
            finished = true;
        } catch (IOException error) {
            throw failure("Cannot update archive: " + container, error);
        } finally {
            freeQuietly(output);
            freeQuietly(input);
            if (!finished && temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static long openReader(String container) throws ArchiveException {
        long archive = Archive.readNew();
        try {
            Archive.readSupportFilterAll(archive);
            Archive.readSupportFormatAll(archive);
            Archive.readOpenFileName(archive, utf8(container), BUFFER_BYTES);
            return archive;
        } catch (ArchiveException error) {
            freeQuietly(archive);
            throw error;
        }
    }

    private static void validate(String path) throws ArchiveException {
        long archive = openReader(path);
        try {
            Archive.readNextHeader(archive);
        } finally {
            freeQuietly(archive);
        }
    }

    private static void writeEmptyEntry(long output, NewEntry addition) throws ArchiveException {
        long entry = ArchiveEntry.new1();
        try {
            String pathname = addition.folder && !addition.pathname.endsWith("/")
                    ? addition.pathname + "/" : addition.pathname;
            ArchiveEntry.setPathnameUtf8(entry, pathname);
            ArchiveEntry.setFiletype(entry, addition.folder ? ArchiveEntry.AE_IFDIR : ArchiveEntry.AE_IFREG);
            ArchiveEntry.setPerm(entry, addition.folder ? 0755 : 0644);
            ArchiveEntry.setSize(entry, 0L);
            ArchiveEntry.setMtime(entry, System.currentTimeMillis() / 1000L, 0L);
            Archive.writeHeader(output, entry);
            Archive.writeFinishEntry(output);
        } finally {
            ArchiveEntry.free(entry);
        }
    }

    private static void appendStorage(long output, java.nio.file.Path source, String entryName)
            throws IOException {
        boolean folder = Files.isDirectory(source);
        long entry = ArchiveEntry.new1();
        try {
            ArchiveEntry.setPathnameUtf8(entry, folder ? entryName + "/" : entryName);
            ArchiveEntry.setFiletype(entry, folder ? ArchiveEntry.AE_IFDIR : ArchiveEntry.AE_IFREG);
            ArchiveEntry.setPerm(entry, folder ? 0755 : 0644);
            ArchiveEntry.setSize(entry, folder ? 0L : Files.size(source));
            ArchiveEntry.setMtime(entry, Files.getLastModifiedTime(source).toMillis() / 1000L, 0L);
            Archive.writeHeader(output, entry);
            if (!folder) copyPhysicalData(source, output);
            Archive.writeFinishEntry(output);
        } finally {
            ArchiveEntry.free(entry);
        }
        if (folder) {
            try (java.nio.file.DirectoryStream<java.nio.file.Path> children =
                         Files.newDirectoryStream(source)) {
                for (java.nio.file.Path child : children) {
                    appendStorage(output, child, entryName + "/" + child.getFileName());
                }
            }
        }
    }

    private static void copyPhysicalData(java.nio.file.Path source, long output) throws IOException {
        byte[] bytes = new byte[BUFFER_BYTES];
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_BYTES);
        try (InputStream stream = Files.newInputStream(source, StandardOpenOption.READ)) {
            int count;
            while ((count = stream.read(bytes)) >= 0) {
                if (count == 0) continue;
                buffer.clear();
                buffer.put(bytes, 0, count);
                buffer.flip();
                while (buffer.hasRemaining()) Archive.writeData(output, buffer);
            }
        }
    }

    private static void copyData(long input, long output) throws ArchiveException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_BYTES);
        while (true) {
            buffer.clear();
            Archive.readData(input, buffer);
            if (buffer.position() == 0) return;
            buffer.flip();
            while (buffer.hasRemaining()) Archive.writeData(output, buffer);
        }
    }

    private static void copyToStream(long input, OutputStream stream)
            throws ArchiveException, IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_BYTES);
        byte[] bytes = new byte[BUFFER_BYTES];
        while (true) {
            buffer.clear();
            Archive.readData(input, buffer);
            int count = buffer.position();
            if (count == 0) return;
            buffer.flip();
            buffer.get(bytes, 0, count);
            stream.write(bytes, 0, count);
        }
    }

    private static void replace(java.nio.file.Path temp, java.nio.file.Path original)
            throws IOException {
        try {
            Files.move(temp, original, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException noAtomicMove) {
            Files.move(temp, original, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void configureWriter(long output, String container) throws ArchiveException {
        String lower = container.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".apk") || lower.endsWith(".jar")
                || lower.endsWith(".war") || lower.endsWith(".aar")) {
            Archive.writeSetFormatZip(output);
        } else {
            Archive.writeSetFormatFilterByExt(output, utf8(container));
        }
    }

    private static Path requireEntry(Entry item) throws FileOperationException {
        if (!item.isInsideArchive()) throw new ArchiveOperationException("Item is not an archive entry");
        return item.path();
    }

    private static void requireArchive(Path path) throws FileOperationException {
        if (!path.isInsideArchive()) throw new ArchiveOperationException("Path is not archive content");
    }

    private void requireWritable(Path path) throws FileOperationException {
        requireArchive(path);
        if (!canWrite(path)) {
            throw new ArchiveOperationException("This archive format is read-only");
        }
    }

    private String containerPath(Path location) {
        return storage.fileAtStoragePath(location.storagePath()).getAbsolutePath();
    }

    private static String prefix(Path path) {
        String name = entryName(path.archiveInnerPath());
        return name.isEmpty() ? "" : name + "/";
    }

    private static String entryName(String path) {
        String result = normalized(path);
        while (result.endsWith("/") && !result.isEmpty()) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String normalized(String path) {
        if (path == null) return "";
        String result = path.replace('\\', '/');
        while (result.startsWith("/")) result = result.substring(1);
        while (result.startsWith("./")) result = result.substring(2);
        return result;
    }

    private static String child(String parent, String name) {
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    private static String parentInner(String path) {
        int separator = path.lastIndexOf('/');
        return separator <= 0 ? "/" : path.substring(0, separator);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String safeFileName(String name) {
        return name.replace('/', '_').replace('\\', '_');
    }

    private static java.nio.file.Path safeResolve(java.nio.file.Path target, String relative)
            throws ArchiveOperationException {
        java.nio.file.Path resolved = target.resolve(relative).normalize();
        if (!resolved.startsWith(target.normalize())) {
            throw new ArchiveOperationException("Archive contains an unsafe entry path");
        }
        return resolved;
    }

    private static void deleteTemporaryTree(java.nio.file.Path root) {
        if (root == null) return;
        try (java.util.stream.Stream<java.nio.file.Path> contents = Files.walk(root)) {
            contents.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static ArchiveOperationException failure(String message, Exception cause) {
        return new ArchiveOperationException(message, cause);
    }

    private static void freeQuietly(long archive) {
        if (archive == 0L) return;
        try {
            Archive.free(archive);
        } catch (ArchiveException ignored) {
        }
    }

    private interface RenameTransform {
        String nameFor(String original);
    }

    private interface ArchiveAppender {
        void append(long output) throws IOException;
    }

    private record NewEntry(String pathname, boolean folder) {
    }

    private record ListedEntry(String name, boolean folder, long size, long modifiedAt) {
    }

}

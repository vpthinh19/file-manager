package com.vpt.filemanager.data.archive;

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

import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.item.ItemFactory;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.core.error.ArchiveOperationException;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.error.NameConflictException;

/**
 * Streaming libarchive gateway. Mutations build a validated replacement before touching the
 * original container; it does not retain an archive tree between pane refreshes.
 */
@Singleton
public final class ArchiveRepository {
    private static final int BUFFER_BYTES = 64 * 1024;

    private final ItemFactory items;
    private final java.nio.file.Path extractionDirectory;

    @Inject
    public ArchiveRepository(ItemFactory items, @ApplicationContext Context context) {
        this.items = items;
        this.extractionDirectory = context.getCacheDir().toPath().resolve("archive-preview");
    }

    @NonNull
    public List<Item> list(@NonNull Path location) throws FileOperationException {
        requireArchive(location);
        String prefix = prefix(location);
        Map<String, ListedEntry> children = new LinkedHashMap<>();
        long archive = 0L;
        try {
            archive = openReader(location.container());
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
            throw failure("Cannot read archive: " + location.container(), error);
        } finally {
            freeQuietly(archive);
        }
        List<Item> result = new ArrayList<>(children.size());
        for (ListedEntry entry : children.values()) {
            String child = child(location.directory(), entry.name);
            result.add(items.archive(Path.archive(location.container(), child), entry.name,
                    entry.folder, entry.size, entry.modifiedAt));
        }
        return result;
    }

    public boolean exists(@NonNull Path directory, @NonNull String name) throws FileOperationException {
        for (Item child : list(directory)) {
            if (child.name().equals(name)) return true;
        }
        return false;
    }

    public boolean canWrite(@NonNull Path location) {
        return location.isArchive() && ArchiveFormat.isWritable(location.container());
    }

    public void create(@NonNull Path directory, @NonNull String name, boolean folder)
            throws FileOperationException {
        requireWritable(directory);
        String pathname = entryName(child(directory.directory(), name));
        if (exists(directory, name)) throw new NameConflictException(name);
        rewrite(directory.container(), original -> original,
                output -> writeEmptyEntry(output, new NewEntry(pathname, folder)));
    }

    public void rename(@NonNull Item item, @NonNull String newName) throws FileOperationException {
        Path source = requireEntry(item);
        requireWritable(source);
        String oldName = entryName(source.directory());
        String parent = parentInner(source.directory());
        String replacement = entryName(child(parent, newName));
        if (oldName.equals(replacement)) return;
        if (exists(Path.archive(source.container(), parent), newName)) {
            throw new NameConflictException(newName);
        }
        rewrite(source.container(), original -> {
            if (original.equals(oldName)) return replacement;
            if (original.startsWith(oldName + "/")) {
                return replacement + original.substring(oldName.length());
            }
            return original;
        }, null);
    }

    public void delete(@NonNull List<Item> selected) throws FileOperationException {
        if (selected.isEmpty()) return;
        Path first = requireEntry(selected.get(0));
        requireWritable(first);
        List<String> removed = new ArrayList<>();
        for (Item item : selected) {
            Path entry = requireEntry(item);
            if (!entry.container().equals(first.container())) {
                throw new ArchiveOperationException("Archive selection crosses containers");
            }
            removed.add(entryName(entry.directory()));
        }
        rewrite(first.container(), original -> {
            for (String path : removed) {
                if (original.equals(path) || original.startsWith(path + "/")) return null;
            }
            return original;
        }, null);
    }

    public void importFromStorage(@NonNull Path destination, @NonNull Item source,
                                  @NonNull String name, boolean replace)
            throws FileOperationException {
        requireWritable(destination);
        if (!source.isLocalActionTarget()) {
            throw new ArchiveOperationException("Only local items can be imported into an archive");
        }
        String importedRoot = entryName(child(destination.directory(), name));
        rewrite(destination.container(), original -> {
            if (replace && (original.equals(importedRoot)
                    || original.startsWith(importedRoot + "/"))) return null;
            return original;
        }, output -> appendStorage(output, java.nio.file.Paths.get(source.localPath()), importedRoot));
    }

    public void updateFromMaterialized(@NonNull Path target, @NonNull String materialized)
            throws FileOperationException {
        requireWritable(target);
        String entry = entryName(target.directory());
        java.nio.file.Path source = java.nio.file.Paths.get(materialized);
        rewrite(target.container(), original -> original.equals(entry) ? null : original,
                output -> appendStorage(output, source, entry));
    }

    public void extractToStorage(@NonNull Item source, @NonNull String destination)
            throws FileOperationException {
        Path entryPath = requireEntry(source);
        String rootName = entryName(entryPath.directory());
        java.nio.file.Path target = java.nio.file.Paths.get(destination);
        long archive = 0L;
        boolean found = false;
        try {
            archive = openReader(entryPath.container());
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
    public String materialize(@NonNull Item item) throws FileOperationException {
        Path target = requireEntry(item);
        if (item.isFolder()) throw new ArchiveOperationException("Cannot open an archive folder as a file");
        String wanted = entryName(target.directory());
        long archive = 0L;
        try {
            Files.createDirectories(extractionDirectory);
            java.nio.file.Path output = extractionDirectory.resolve(
                    Integer.toHexString(item.key().hashCode()) + "-" + safeFileName(item.name()));
            archive = openReader(target.container());
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

    private static Path requireEntry(Item item) throws FileOperationException {
        if (!item.isArchiveEntry()) throw new ArchiveOperationException("Item is not an archive entry");
        return item.archiveEntry();
    }

    private static void requireArchive(Path path) throws FileOperationException {
        if (!path.isArchive()) throw new ArchiveOperationException("Path is not an archive location");
    }

    private static void requireWritable(Path path) throws FileOperationException {
        requireArchive(path);
        if (!ArchiveFormat.isWritable(path.container())) {
            throw new ArchiveOperationException("This archive format is read-only");
        }
    }

    private static String prefix(Path path) {
        String name = entryName(path.directory());
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

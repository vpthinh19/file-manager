package com.vpt.filemanager.storage.virtual.archive;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.error.ArchiveOperationException;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.error.NameConflictException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.physical.local.LocalStorageAdapter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import me.zhanghai.android.libarchive.Archive;
import me.zhanghai.android.libarchive.ArchiveEntry;
import me.zhanghai.android.libarchive.ArchiveException;

/**
 * Libarchive backend for {@code ArchiveStorage}. Physical files, cache files and replacements are
 * created and mutated only by {@link LocalStorageAdapter}; native archive calls receive adapter
 * handles as pathname arguments.
 */
@Singleton
public final class ArchiveBackend {
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final int MAX_NESTED_DEPTH = 8;
    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_MATERIALIZED_BYTES = 512L * 1024L * 1024L;

    private final LocalStorageAdapter storage;
    private final Map<String, Object> containerLocks = new ConcurrentHashMap<>();

    @Inject
    public ArchiveBackend(LocalStorageAdapter storage) {
        this.storage = storage;
    }

    @NonNull
    public List<Entry> list(@NonNull Path location) throws FileOperationException {
        requireArchive(location);
        String prefix = prefix(location);
        Map<String, ListedEntry> children = new LinkedHashMap<>();
        long archive = 0L;
        try {
            archive = openReader(container(location));
            long header;
            int count = 0;
            while ((header = Archive.readNextHeader(archive)) != 0L) {
                if (++count > MAX_ENTRIES) {
                    throw new ArchiveOperationException("Archive contains too many entries");
                }
                if (ArchiveEntry.isEncrypted(header)) {
                    throw new ArchiveOperationException("Encrypted archives are not supported");
                }
                String pathname = safeVisiblePath(header);
                if (pathname != null && pathname.startsWith(prefix)) {
                    String remaining = pathname.substring(prefix.length());
                    if (!remaining.isEmpty()) {
                        int separator = remaining.indexOf('/');
                        String name = separator < 0 ? remaining : remaining.substring(0, separator);
                        boolean folder = separator >= 0 || ArchiveEntry.filetype(header) == ArchiveEntry.AE_IFDIR;
                        ListedEntry found = new ListedEntry(name, folder,
                                folder ? -1L : ArchiveEntry.size(header),
                                ArchiveEntry.mtime(header) * 1000L);
                        ListedEntry previous = children.get(name);
                        if (previous == null || found.folder && !previous.folder) {
                            children.put(name, found);
                        }
                    }
                }
                Archive.readDataSkip(archive);
            }
            rejectEncryption(archive);
        } catch (ArchiveOperationException error) {
            throw error;
        } catch (ArchiveException error) {
            throw failure("Cannot read archive: " + location.serialize(), error);
        } finally {
            freeQuietly(archive);
        }
        List<Entry> result = new ArrayList<>(children.size());
        for (ListedEntry entry : children.values()) {
            result.add(Entry.archive(location.child(entry.name), entry.name, entry.folder,
                    entry.size, entry.modifiedAt));
        }
        return result;
    }

    public boolean isDirectory(@NonNull Path location) throws FileOperationException {
        requireArchive(location);
        if ("/".equals(location.archiveInnerPath())) return true;
        String wanted = entryName(location.archiveInnerPath());
        long archive = 0L;
        try {
            archive = openReader(container(location));
            long header;
            while ((header = Archive.readNextHeader(archive)) != 0L) {
                if (ArchiveEntry.isEncrypted(header)) {
                    throw new ArchiveOperationException("Encrypted archives are not supported");
                }
                String candidate = safeVisiblePath(header);
                if (candidate == null) {
                    Archive.readDataSkip(archive);
                    continue;
                }
                candidate = entryName(candidate);
                if (candidate.equals(wanted)) return ArchiveEntry.filetype(header) == ArchiveEntry.AE_IFDIR;
                if (candidate.startsWith(wanted + "/")) return true;
                Archive.readDataSkip(archive);
            }
            rejectEncryption(archive);
            throw new ArchiveOperationException("Archive entry no longer exists");
        } catch (ArchiveOperationException error) {
            throw error;
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
        if (!location.isInsideArchive() || location.archiveDepth() > MAX_NESTED_DEPTH) return false;
        try {
            List<File> containers = containers(location);
            for (File container : containers) {
                long archive = 0L;
                try {
                    archive = openReader(container);
                    Archive.readNextHeader(archive);
                    rejectEncryption(archive);
                    if (!writableFormat(Archive.format(archive) & Archive.FORMAT_BASE_MASK)) return false;
                } finally {
                    freeQuietly(archive);
                }
            }
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    public void create(@NonNull Path directory, @NonNull String name, boolean folder)
            throws FileOperationException {
        requireWritable(directory);
        String pathname = entryName(child(directory.archiveInnerPath(), name));
        if (exists(directory, name)) throw new NameConflictException(name);
        mutate(directory, original -> original,
                output -> writeEmptyEntry(output, new NewEntry(pathname, folder)));
    }

    public void rename(@NonNull Entry item, @NonNull String newName) throws FileOperationException {
        Path source = requireEntry(item);
        requireWritable(source);
        String oldName = entryName(source.archiveInnerPath());
        String parent = parentInner(source.archiveInnerPath());
        String replacement = entryName(child(parent, newName));
        if (oldName.equals(replacement)) return;
        Path directory = source.parent();
        if (directory != null && exists(directory, newName)) throw new NameConflictException(newName);
        mutate(source, original -> {
            if (original.equals(oldName)) return replacement;
            if (original.startsWith(oldName + "/")) return replacement + original.substring(oldName.length());
            return original;
        }, null);
    }

    public void delete(@NonNull List<Entry> selected) throws FileOperationException {
        if (selected.isEmpty()) return;
        Path first = requireEntry(selected.get(0));
        requireWritable(first);
        List<String> removed = new ArrayList<>();
        for (Entry item : selected) {
            Path path = requireEntry(item);
            if (!sameContainer(first, path)) {
                throw new ArchiveOperationException("Archive selection crosses containers");
            }
            removed.add(entryName(path.archiveInnerPath()));
        }
        mutate(first, original -> {
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
            throw new ArchiveOperationException("Only device items can be imported into an archive");
        }
        importFile(destination, storage.fromAbsolutePath(source.localPath()), name, replace);
    }

    public void importFromArchive(@NonNull Path destination, @NonNull Entry source,
                                  @NonNull String name, boolean replace)
            throws FileOperationException {
        requireEntry(source);
        File transfer = storage.workFile("archive-transfer", source.key(), source.name());
        if (storage.exists(transfer)) storage.deletePermanently(transfer);
        if (source.isFolder()) extractToStorage(source, transfer.getAbsolutePath());
        else extractExact(source, transfer, false);
        importFile(destination, transfer, name, replace);
    }

    private void importFile(Path destination, File source, String name, boolean replace)
            throws FileOperationException {
        requireWritable(destination);
        String importedRoot = entryName(child(destination.archiveInnerPath(), name));
        mutate(destination, original -> {
            if (replace && (original.equals(importedRoot) || original.startsWith(importedRoot + "/"))) {
                return null;
            }
            return original;
        }, output -> appendStorage(output, source, importedRoot));
    }

    public void updateFromMaterialized(@NonNull Path target, @NonNull String materialized)
            throws FileOperationException {
        requireWritable(target);
        String entry = entryName(target.archiveInnerPath());
        File source = storage.fromAbsolutePath(materialized);
        mutate(target, original -> original.equals(entry) ? null : original,
                output -> appendStorage(output, source, entry));
    }

    public void extractToStorage(@NonNull Entry source, @NonNull String destination)
            throws FileOperationException {
        Path sourcePath = requireEntry(source);
        File target = storage.fromAbsolutePath(destination);
        if (!source.isFolder()) {
            extractExact(source, target, false);
            return;
        }
        String rootName = entryName(sourcePath.archiveInnerPath());
        long archive = 0L;
        boolean found = false;
        try {
            archive = openReader(container(sourcePath));
            long header;
            while ((header = Archive.readNextHeader(archive)) != 0L) {
                String pathname = safeVisiblePath(header);
                if (pathname == null || !(pathname.equals(rootName) || pathname.startsWith(rootName + "/"))) {
                    Archive.readDataSkip(archive);
                    continue;
                }
                if (ArchiveEntry.isEncrypted(header)) {
                    throw new ArchiveOperationException("Encrypted archives are not supported");
                }
                found = true;
                String relative = pathname.equals(rootName) ? "" : pathname.substring(rootName.length() + 1);
                File output = relative.isEmpty() ? target : storage.safeDescendant(target, relative);
                if (ArchiveEntry.filetype(header) == ArchiveEntry.AE_IFDIR || relative.isEmpty()) {
                    storage.ensureDirectory(output);
                    Archive.readDataSkip(archive);
                } else {
                    try (OutputStream stream = storage.openCreateWrite(output)) {
                        copyToStream(archive, stream);
                    }
                }
            }
            rejectEncryption(archive);
            if (!found) throw new ArchiveOperationException("Archive entry no longer exists: " + source.name());
        } catch (ArchiveOperationException error) {
            throw error;
        } catch (Exception error) {
            throw failure("Cannot extract archive entry: " + source.name(), error);
        } finally {
            freeQuietly(archive);
        }
    }

    public void extractToStorage(@NonNull Entry source, @NonNull Path destinationParent,
                                 @NonNull String name, boolean replace)
            throws FileOperationException {
        File target = storage.target(storage.fileAtStoragePath(destinationParent.storagePath()), name);
        if (source.isFolder()) {
            if (storage.exists(target) && !storage.isDirectory(target)) {
                if (!replace) throw new NameConflictException(name);
                storage.deletePermanently(target);
            }
            extractToStorage(source, target.getAbsolutePath());
            return;
        }
        File staged = storage.createTemporarySibling(target);
        try {
            extractExact(source, staged, false);
            storage.replace(staged, target);
        } finally {
            if (storage.exists(staged)) storage.deletePermanently(staged);
        }
    }

    @NonNull
    public String materialize(@NonNull Entry item) throws FileOperationException {
        if (item.isFolder()) throw new ArchiveOperationException("Cannot open an archive folder as a file");
        if (item.size() > MAX_MATERIALIZED_BYTES) {
            throw new ArchiveOperationException("File is too large to open");
        }
        File output = storage.workFile("archive-preview", item.key(), item.name());
        extractExact(item, output, true);
        return output.getAbsolutePath();
    }

    private void extractExact(Entry source, File output) throws FileOperationException {
        extractExact(source, output, true);
    }

    private void extractExact(Entry source, File output, boolean enforceMaterializationLimit)
            throws FileOperationException {
        Path target = requireEntry(source);
        String wanted = entryName(target.archiveInnerPath());
        long archive = 0L;
        try {
            archive = openReader(container(target));
            long header;
            while ((header = Archive.readNextHeader(archive)) != 0L) {
                String pathname = safeVisiblePath(header);
                if (pathname != null && entryName(pathname).equals(wanted)) {
                    if (ArchiveEntry.isEncrypted(header)) {
                        throw new ArchiveOperationException("Encrypted archives are not supported");
                    }
                    if (ArchiveEntry.filetype(header) != ArchiveEntry.AE_IFREG) {
                        throw new ArchiveOperationException("Archive entry cannot be materialized");
                    }
                    if (enforceMaterializationLimit
                            && ArchiveEntry.size(header) > MAX_MATERIALIZED_BYTES) {
                        throw new ArchiveOperationException("File is too large to open");
                    }
                    try (OutputStream stream = storage.openCreateWrite(output)) {
                        copyToStream(archive, stream);
                    }
                    return;
                }
                Archive.readDataSkip(archive);
            }
            rejectEncryption(archive);
            throw new ArchiveOperationException("Archive entry no longer exists: " + source.name());
        } catch (ArchiveOperationException error) {
            throw error;
        } catch (Exception error) {
            throw failure("Cannot extract archive entry: " + source.name(), error);
        } finally {
            freeQuietly(archive);
        }
    }

    private void mutate(Path location, RenameTransform transform, ArchiveAppender addition)
            throws FileOperationException {
        Object lock = lock(location);
        synchronized (lock) {
            List<File> chain = containers(location);
            int deepest = chain.size() - 1;
            rewrite(chain.get(deepest), containerName(location, deepest), transform, addition);
            for (int level = deepest - 1; level >= 0; level--) {
                String nestedEntry = entryName(location.archivePaths().get(level));
                File nestedContainer = chain.get(level + 1);
                rewrite(chain.get(level), containerName(location, level),
                        original -> original.equals(nestedEntry) ? null : original,
                        output -> appendStorage(output, nestedContainer, nestedEntry));
            }
        }
    }

    private void rewrite(File original, String logicalName, RenameTransform transform,
                         ArchiveAppender addition) throws FileOperationException {
        File temporary = storage.createTemporarySibling(original);
        long input = 0L;
        long output = 0L;
        boolean replaced = false;
        try {
            input = openReader(original);
            output = Archive.writeNew();
            configureWriter(output, logicalName, input);
            Archive.writeOpenFileName(output, utf8(temporary.getAbsolutePath()));
            long header;
            while ((header = Archive.readNextHeader(input)) != 0L) {
                String safeName = safeVisiblePath(header);
                if (safeName == null || ArchiveEntry.isEncrypted(header)) {
                    Archive.readDataSkip(input);
                    continue;
                }
                String writtenName = transform.nameFor(safeName);
                if (writtenName == null) {
                    Archive.readDataSkip(input);
                    continue;
                }
                long copy = ArchiveEntry.clone(header);
                try {
                    ArchiveEntry.setPathnameUtf8(copy, writtenName);
                    Archive.writeHeader(output, copy);
                    copyData(input, output);
                    Archive.writeFinishEntry(output);
                } finally {
                    ArchiveEntry.free(copy);
                }
            }
            rejectEncryption(input);
            if (addition != null) addition.append(output);
            Archive.writeClose(output);
            Archive.free(output);
            output = 0L;
            validate(temporary);
            storage.replace(temporary, original);
            replaced = true;
        } catch (FileOperationException error) {
            throw error;
        } catch (Exception error) {
            throw failure("Cannot update archive: " + logicalName, error);
        } finally {
            freeQuietly(output);
            freeQuietly(input);
            if (!replaced && storage.exists(temporary)) storage.deletePermanently(temporary);
        }
    }

    @NonNull
    private List<File> containers(Path location) throws FileOperationException {
        requireArchive(location);
        if (location.archiveDepth() > MAX_NESTED_DEPTH) {
            throw new ArchiveOperationException("Nested archive depth limit exceeded");
        }
        synchronized (lock(location)) {
            List<File> chain = new ArrayList<>();
            File current = storage.fileAtStoragePath(location.storagePath());
            chain.add(current);
            for (int level = 0; level < location.archiveDepth() - 1; level++) {
                String nestedPath = location.archivePaths().get(level);
                String name = fileName(nestedPath);
                File materialized = storage.workFile("archive-containers",
                        location.storagePath() + location.archivePaths().subList(0, level + 1), name);
                extractFromContainer(current, nestedPath, materialized);
                chain.add(materialized);
                current = materialized;
            }
            return chain;
        }
    }

    private File container(Path location) throws FileOperationException {
        List<File> chain = containers(location);
        return chain.get(chain.size() - 1);
    }

    private void extractFromContainer(File container, String innerPath, File output)
            throws FileOperationException {
        String wanted = entryName(innerPath);
        long archive = 0L;
        try {
            archive = openReader(container);
            long header;
            while ((header = Archive.readNextHeader(archive)) != 0L) {
                String pathname = safeVisiblePath(header);
                if (pathname != null && entryName(pathname).equals(wanted)) {
                    if (ArchiveEntry.isEncrypted(header)) {
                        throw new ArchiveOperationException("Encrypted archives are not supported");
                    }
                    if (ArchiveEntry.size(header) > MAX_MATERIALIZED_BYTES) {
                        throw new ArchiveOperationException("Nested archive is too large to open");
                    }
                    try (OutputStream stream = storage.openCreateWrite(output)) {
                        copyToStream(archive, stream);
                    }
                    return;
                }
                Archive.readDataSkip(archive);
            }
            throw new ArchiveOperationException("Nested archive no longer exists");
        } catch (ArchiveOperationException error) {
            throw error;
        } catch (Exception error) {
            throw failure("Cannot open nested archive", error);
        } finally {
            freeQuietly(archive);
        }
    }

    private Object lock(Path path) {
        return containerLocks.computeIfAbsent(path.storagePath(), ignored -> new Object());
    }

    private static long openReader(File container) throws ArchiveException {
        long archive = Archive.readNew();
        try {
            Archive.readSupportFilterAll(archive);
            Archive.readSupportFormatAll(archive);
            Archive.readOpenFileName(archive, utf8(container.getAbsolutePath()), BUFFER_BYTES);
            return archive;
        } catch (ArchiveException error) {
            freeQuietly(archive);
            throw error;
        }
    }

    private static void validate(File file) throws ArchiveException {
        long archive = openReader(file);
        try {
            Archive.readNextHeader(archive);
        } finally {
            freeQuietly(archive);
        }
    }

    private void appendStorage(long output, File source, String entryName) throws Exception {
        boolean folder = storage.isDirectory(source);
        long entry = ArchiveEntry.new1();
        try {
            ArchiveEntry.setPathnameUtf8(entry, folder ? entryName + "/" : entryName);
            ArchiveEntry.setFiletype(entry, folder ? ArchiveEntry.AE_IFDIR : ArchiveEntry.AE_IFREG);
            ArchiveEntry.setPerm(entry, folder ? 0755 : 0644);
            ArchiveEntry.setSize(entry, folder ? 0L : storage.size(source));
            ArchiveEntry.setMtime(entry, storage.modifiedAt(source) / 1000L, 0L);
            Archive.writeHeader(output, entry);
            if (!folder) copyPhysicalData(source, output);
            Archive.writeFinishEntry(output);
        } finally {
            ArchiveEntry.free(entry);
        }
        if (folder) {
            for (File child : storage.children(source)) {
                appendStorage(output, child, entryName + "/" + storage.name(child));
            }
        }
    }

    private void copyPhysicalData(File source, long output) throws Exception {
        byte[] bytes = new byte[BUFFER_BYTES];
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_BYTES);
        try (InputStream stream = storage.openRead(source)) {
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

    private static void configureWriter(long output, String logicalName, long input)
            throws ArchiveException, ArchiveOperationException {
        if (ArchiveFormat.isWritable(logicalName)) {
            Archive.writeSetFormatFilterByExt(output, utf8(logicalName));
            return;
        }
        switch (Archive.format(input) & Archive.FORMAT_BASE_MASK) {
            case Archive.FORMAT_ZIP -> Archive.writeSetFormatZip(output);
            case Archive.FORMAT_7ZIP -> Archive.writeSetFormat7zip(output);
            case Archive.FORMAT_TAR -> Archive.writeSetFormatPaxRestricted(output);
            case Archive.FORMAT_CPIO -> Archive.writeSetFormatCpioNewc(output);
            case Archive.FORMAT_AR -> Archive.writeSetFormatArSvr4(output);
            case Archive.FORMAT_XAR -> Archive.writeSetFormatXar(output);
            case Archive.FORMAT_WARC -> Archive.writeSetFormatWarc(output);
            default -> throw new ArchiveOperationException("Archive format is read-only");
        }
    }

    private static boolean writableFormat(int format) {
        return format == Archive.FORMAT_ZIP || format == Archive.FORMAT_7ZIP
                || format == Archive.FORMAT_TAR || format == Archive.FORMAT_CPIO
                || format == Archive.FORMAT_AR || format == Archive.FORMAT_XAR
                || format == Archive.FORMAT_WARC;
    }

    private static void rejectEncryption(long archive) throws ArchiveOperationException {
        if (Archive.readHasEncryptedEntries(archive) > 0) {
            throw new ArchiveOperationException("Encrypted archives are not supported");
        }
    }

    private static String safeVisiblePath(long entry) {
        int type = ArchiveEntry.filetype(entry);
        if (type != ArchiveEntry.AE_IFREG && type != ArchiveEntry.AE_IFDIR) return null;
        if (ArchiveEntry.symlink(entry) != null || ArchiveEntry.hardlinkIsSet(entry)) return null;
        String raw = ArchiveEntry.pathnameUtf8(entry);
        if (raw == null || raw.startsWith("/") || raw.startsWith("\\")) return null;
        String normalized = raw.replace('\\', '/');
        for (String section : normalized.split("/")) {
            if ("..".equals(section) || ".".equals(section)) return null;
        }
        return normalized(normalized);
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
        if (!canWrite(path)) throw new ArchiveOperationException("This archive format is read-only");
    }

    private static boolean sameContainer(Path left, Path right) {
        return left.storagePath().equals(right.storagePath())
                && left.archivePaths().subList(0, left.archivePaths().size() - 1)
                .equals(right.archivePaths().subList(0, right.archivePaths().size() - 1));
    }

    private static String containerName(Path location, int level) {
        return level == 0 ? location.storagePath() : location.archivePaths().get(level - 1);
    }

    private static String prefix(Path path) {
        String name = entryName(path.archiveInnerPath());
        return name.isEmpty() ? "" : name + "/";
    }

    private static String entryName(String path) {
        String result = normalized(path);
        while (result.endsWith("/") && !result.isEmpty()) result = result.substring(0, result.length() - 1);
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

    private static String fileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
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
        void append(long output) throws Exception;
    }

    private record NewEntry(String pathname, boolean folder) {
    }

    private record ListedEntry(String name, boolean folder, long size, long modifiedAt) {
    }
}

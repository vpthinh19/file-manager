package com.vpt.filemanager.node.source.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;

/**
 * ZIP-compatible archive writer using temp-file rewrite followed by atomic replacement.
 *
 * <p>This implements writable nodes for ZIP/JAR/APK/AAR/WAR containers already handled by
 * {@code ZipFile}. TAR/7z/RAR support remains the responsibility of the native libarchive
 * backend when its library is vendored and linked.
 */
@Singleton
public final class ZipArchiveMutationBackend implements ArchiveMutationBackend {
    private static final int BUFFER_BYTES = 64 * 1024;

    @Inject
    public ZipArchiveMutationBackend() {
    }

    @Override
    public synchronized void createFile(NodePath archiveFile, String innerPath)
            throws NodeException {
        String entryName = fileEntryName(innerPath);
        rewrite(archiveFile, (input, output) -> {
            requireAbsent(input, entryName);
            copyEntries(input, output, name -> name, name -> false);
            output.putNextEntry(newEntry(entryName));
            output.closeEntry();
        });
    }

    @Override
    public synchronized void createFolder(NodePath archiveFile, String innerPath)
            throws NodeException {
        String entryName = directoryEntryName(innerPath);
        rewrite(archiveFile, (input, output) -> {
            requireAbsent(input, entryName);
            copyEntries(input, output, name -> name, name -> false);
            output.putNextEntry(newEntry(entryName));
            output.closeEntry();
        });
    }

    @Override
    public synchronized void replaceFile(NodePath archiveFile, String innerPath, Path payload)
            throws NodeException {
        String entryName = fileEntryName(innerPath);
        rewrite(archiveFile, (input, output) -> {
            ZipEntry current = input.getEntry(entryName);
            if (current == null || current.isDirectory()) {
                throw new NodeException("Archive entry not found: " + entryName);
            }
            copyEntries(input, output, name -> name, name -> name.equals(entryName));
            ZipEntry replacement = newEntry(entryName);
            output.putNextEntry(replacement);
            try (InputStream bytes = Files.newInputStream(payload)) {
                transfer(bytes, output);
            }
            output.closeEntry();
        });
    }

    @Override
    public synchronized void rename(NodePath archiveFile,
                                    String fromInnerPath,
                                    String toInnerPath,
                                    boolean folder) throws NodeException {
        String from = folder ? directoryEntryName(fromInnerPath) : fileEntryName(fromInnerPath);
        String to = folder ? directoryEntryName(toInnerPath) : fileEntryName(toInnerPath);
        rewrite(archiveFile, (input, output) -> {
            requireAbsent(input, to);
            boolean[] found = {false};
            copyEntries(input, output, name -> {
                if (folder && name.startsWith(from)) {
                    found[0] = true;
                    return to + name.substring(from.length());
                }
                if (!folder && name.equals(from)) {
                    found[0] = true;
                    return to;
                }
                return name;
            }, name -> false);
            if (!found[0]) {
                throw new NodeException("Archive entry not found: " + from);
            }
        });
    }

    @Override
    public synchronized void delete(NodePath archiveFile, String innerPath, boolean folder)
            throws NodeException {
        String target = folder ? directoryEntryName(innerPath) : fileEntryName(innerPath);
        rewrite(archiveFile, (input, output) -> {
            boolean[] found = {false};
            copyEntries(input, output, name -> name, name -> {
                boolean remove = folder ? name.startsWith(target) : name.equals(target);
                if (remove) {
                    found[0] = true;
                }
                return remove;
            });
            if (!found[0]) {
                throw new NodeException("Archive entry not found: " + target);
            }
        });
    }

    private void rewrite(NodePath archiveFile, Edit edit) throws NodeException {
        Path physical = physicalPath(archiveFile);
        Path parent = physical.getParent();
        if (parent == null) {
            throw new NodeException("Archive has no parent directory: " + archiveFile.path());
        }
        Path temp = null;
        try {
            temp = Files.createTempFile(parent, "." + physical.getFileName() + "-", ".rewrite");
            try (ZipFile input = new ZipFile(physical.toFile());
                 ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temp))) {
                edit.apply(input, output);
            }
            try {
                Files.move(temp, physical,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, physical, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException | SecurityException error) {
            throw new NodeException("Unable to update archive: " + archiveFile.name(), error);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void copyEntries(ZipFile input,
                                    ZipOutputStream output,
                                    EntryNameMapper mapper,
                                    EntryFilter skip)
            throws IOException, NodeException {
        Enumeration<? extends ZipEntry> entries = input.entries();
        while (entries.hasMoreElements()) {
            ZipEntry original = entries.nextElement();
            String originalName = original.getName();
            if (skip.test(originalName)) {
                continue;
            }
            ZipEntry copy = new ZipEntry(mapper.map(originalName));
            if (original.getTime() >= 0) {
                copy.setTime(original.getTime());
            }
            output.putNextEntry(copy);
            if (!original.isDirectory()) {
                try (InputStream bytes = input.getInputStream(original)) {
                    transfer(bytes, output);
                }
            }
            output.closeEntry();
        }
    }

    private static void requireAbsent(ZipFile input, String entryName) throws NodeException {
        String logical = entryName.endsWith("/")
                ? entryName.substring(0, entryName.length() - 1) : entryName;
        Enumeration<? extends ZipEntry> entries = input.entries();
        while (entries.hasMoreElements()) {
            String present = entries.nextElement().getName();
            if (present.equals(logical) || present.equals(logical + "/")
                    || present.startsWith(logical + "/")) {
                throw new NodeException("Archive entry already exists: " + logical);
            }
        }
    }

    private static ZipEntry newEntry(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(System.currentTimeMillis());
        return entry;
    }

    private static void transfer(InputStream input, ZipOutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
    }

    private static String fileEntryName(String innerPath) throws NodeException {
        String normalized = normalize(innerPath);
        if (normalized.isEmpty()) {
            throw new NodeException("Cannot mutate archive root");
        }
        return normalized;
    }

    private static String directoryEntryName(String innerPath) throws NodeException {
        return fileEntryName(innerPath) + "/";
    }

    private static String normalize(String innerPath) {
        String value = innerPath == null ? "" : innerPath.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/") && !value.isEmpty()) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static Path physicalPath(NodePath archiveFile) throws NodeException {
        if (!archiveFile.isLocal()) {
            throw new NodeException("Only local archive containers are writable");
        }
        String raw = archiveFile.path();
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                && raw.matches("^/[A-Za-z]:/.*")) {
            raw = raw.substring(1);
        }
        return Paths.get(raw);
    }

    @FunctionalInterface
    private interface Edit {
        void apply(ZipFile input, ZipOutputStream output) throws IOException, NodeException;
    }

    @FunctionalInterface
    private interface EntryNameMapper {
        String map(String name);
    }

    @FunctionalInterface
    private interface EntryFilter {
        boolean test(String name);
    }
}

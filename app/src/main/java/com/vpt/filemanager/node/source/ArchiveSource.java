package com.vpt.filemanager.node.source;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.archive.ArchiveMutationBackend;
import com.vpt.filemanager.node.source.archive.ZipArchiveMutationBackend;

/**
 * ZIP-backed virtual-node source.
 *
 * <p>ZIP central directories are cached to keep navigation responsive, but the cache is bounded.
 * An archive with an open entry stream is pinned until that stream closes; inactive least-recently
 * used sessions are then closed and evicted. Opening unrelated archives therefore cannot retain
 * every {@link ZipFile} for the process lifetime.
 *
 * <p>Writes delegate to {@link ArchiveMutationBackend}: entry mutations rewrite a temporary
 * container and replace the physical archive only after a complete successful rewrite. Operation
 * classes continue to treat archive entries like ordinary virtual nodes.
 */
@Singleton
public final class ArchiveSource implements NodeSource {
    static final int MAX_OPEN_ARCHIVES = 4;

    private final Map<NodePath, ArchiveSession> sessions =
            new LinkedHashMap<>(MAX_OPEN_ARCHIVES + 1, 0.75f, true);
    private final ArchiveMutationBackend mutationBackend;

    public ArchiveSource() {
        this(new ZipArchiveMutationBackend());
    }

    @Inject
    public ArchiveSource(ArchiveMutationBackend mutationBackend) {
        this.mutationBackend = mutationBackend;
    }

    @Override
    public VirtualNode resolve(NodePath path) throws NodeException {
        if (!path.isArchive()) {
            throw new NodeException("ArchiveSource cannot resolve scheme: " + path.scheme());
        }
        if ("/".equals(path.path())) {
            return new VirtualNode(path, true, -1L, -1L, this);
        }
        ArchiveSession session = acquire(NodePath.parse(path.authority()));
        try {
            String inner = stripLeadingSlash(path.path());
            ZipEntry entry = session.zip.getEntry(inner);
            if (entry == null) {
                entry = session.zip.getEntry(inner + "/");
            }
            if (entry == null) {
                String childPrefix = inner + "/";
                boolean implicitDirectory = session.zip.stream()
                        .anyMatch(candidate -> candidate.getName().startsWith(childPrefix));
                if (implicitDirectory) {
                    return new VirtualNode(path, true, -1L, -1L, this);
                }
                throw new NodeException("Archive entry not found: " + inner);
            }
            boolean folder = entry.isDirectory();
            return new VirtualNode(path, folder,
                    folder ? -1L : entry.getSize(), entry.getTime(), this);
        } finally {
            release(session);
        }
    }

    @Override
    public List<VirtualNode> list(VirtualNode folder) throws NodeException {
        NodePath dirPath = folder.path();
        if (!dirPath.isArchive()) {
            throw new NodeException("ArchiveSource cannot list scheme: " + dirPath.scheme());
        }
        NodePath archiveFilePath = NodePath.parse(dirPath.authority());
        ArchiveSession session = acquire(archiveFilePath);
        try {
            return listImmediateChildren(session.zip, archiveFilePath, dirPath.path());
        } finally {
            release(session);
        }
    }

    @Override
    public InputStream read(VirtualNode file) throws NodeException {
        NodePath filePath = file.path();
        if (!filePath.isArchive()) {
            throw new NodeException("ArchiveSource cannot read scheme: " + filePath.scheme());
        }
        ArchiveSession session = acquire(NodePath.parse(filePath.authority()));
        String inner = stripLeadingSlash(filePath.path());
        try {
            ZipEntry entry = session.zip.getEntry(inner);
            if (entry == null || entry.isDirectory()) {
                throw new NodeException("Archive entry not found: " + inner);
            }
            return new SessionInputStream(session.zip.getInputStream(entry), session);
        } catch (IOException | NodeException error) {
            release(session);
            if (error instanceof NodeException) {
                throw (NodeException) error;
            }
            throw new NodeException("Unable to open archive entry: " + inner, error);
        }
    }

    @Override
    public OutputStream openWrite(VirtualNode file) throws NodeException {
        NodePath entryPath = file.path();
        if (!entryPath.isArchive() || file.isFolder()) {
            throw new NodeException("Cannot write archive folder: " + entryPath);
        }
        NodePath archiveFilePath = NodePath.parse(entryPath.authority());
        try {
            Path payload = Files.createTempFile("archive-entry-", ".payload");
            OutputStream staged = Files.newOutputStream(payload,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            return new StagedEntryOutputStream(staged, payload, archiveFilePath, entryPath.path());
        } catch (IOException | SecurityException error) {
            throw new NodeException("Unable to stage archive entry: " + entryPath.name(), error);
        }
    }

    @Override
    public boolean supportsWrite() {
        return true;
    }

    @Override
    public VirtualNode createFile(NodePath path) throws NodeException {
        NodePath archiveFilePath = requireArchiveEntry(path);
        mutate(archiveFilePath, () -> mutationBackend.createFile(archiveFilePath, path.path()));
        return resolve(path);
    }

    @Override
    public VirtualNode createFolder(NodePath path) throws NodeException {
        NodePath archiveFilePath = requireArchiveEntry(path);
        mutate(archiveFilePath, () -> mutationBackend.createFolder(archiveFilePath, path.path()));
        return resolve(path);
    }

    @Override
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        NodePath oldPath = node.path();
        NodePath archiveFilePath = requireArchiveEntry(oldPath);
        NodePath renamedPath = oldPath.parent().child(newName);
        mutate(archiveFilePath, () -> mutationBackend.rename(
                archiveFilePath, oldPath.path(), renamedPath.path(), node.isFolder()));
        return resolve(renamedPath);
    }

    @Override
    public void delete(VirtualNode node) throws NodeException {
        NodePath path = node.path();
        NodePath archiveFilePath = requireArchiveEntry(path);
        mutate(archiveFilePath, () -> mutationBackend.delete(
                archiveFilePath, path.path(), node.isFolder()));
    }

    private static NodePath requireArchiveEntry(NodePath path) throws NodeException {
        if (!path.isArchive() || "/".equals(path.path())) {
            throw new NodeException("Cannot mutate archive root: " + path);
        }
        NodePath archiveFilePath = NodePath.parse(path.authority());
        if (!archiveFilePath.isLocal()) {
            throw new NodeException("Only local archive containers are writable");
        }
        return archiveFilePath;
    }

    private synchronized void mutate(NodePath archiveFilePath, ArchiveMutation mutation)
            throws NodeException {
        ArchiveSession cached = sessions.get(archiveFilePath);
        if (cached != null && cached.activeUses > 0) {
            throw new NodeException("Archive is busy: close open entries before modifying it");
        }
        if (cached != null) {
            sessions.remove(archiveFilePath);
            closeQuietly(cached.zip);
        }
        mutation.run();
    }

    private synchronized ArchiveSession acquire(NodePath archiveFilePath) throws NodeException {
        ArchiveSession existing = sessions.get(archiveFilePath);
        if (existing != null) {
            if (existing.activeUses == 0 && !existing.matches(archiveFilePath)) {
                sessions.remove(archiveFilePath);
                closeQuietly(existing.zip);
            } else {
                existing.activeUses++;
                return existing;
            }
        }
        try {
            ArchiveSession session = new ArchiveSession(archiveFilePath);
            session.activeUses = 1;
            sessions.put(archiveFilePath, session);
            trimInactiveSessions();
            return session;
        } catch (IOException error) {
            throw new NodeException("Unable to open archive: " + archiveFilePath.path(), error);
        }
    }

    private synchronized void release(ArchiveSession session) {
        if (session.activeUses > 0) {
            session.activeUses--;
        }
        trimInactiveSessions();
    }

    private void trimInactiveSessions() {
        while (sessions.size() > MAX_OPEN_ARCHIVES) {
            Iterator<Map.Entry<NodePath, ArchiveSession>> entries = sessions.entrySet().iterator();
            boolean removed = false;
            while (entries.hasNext()) {
                ArchiveSession candidate = entries.next().getValue();
                if (candidate.activeUses == 0) {
                    entries.remove();
                    closeQuietly(candidate.zip);
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                return;
            }
        }
    }

    int cachedSessionCount() {
        synchronized (this) {
            return sessions.size();
        }
    }

    private static void closeQuietly(ZipFile zip) {
        try {
            zip.close();
        } catch (IOException ignored) {
        }
    }

    private List<VirtualNode> listImmediateChildren(
            ZipFile zip,
            NodePath archiveFilePath,
            String innerDir) {
        String prefix = normalizeEntryPrefix(innerDir);
        Set<String> directNames = new HashSet<>();
        List<VirtualNode> nodes = new ArrayList<>(32);
        zip.stream().forEach(entry -> {
            String name = entry.getName();
            if (!name.startsWith(prefix) || name.equals(prefix)) {
                return;
            }
            String remaining = name.substring(prefix.length());
            int slash = remaining.indexOf('/');
            String direct = slash < 0 ? remaining : remaining.substring(0, slash);
            if (direct.isEmpty() || !directNames.add(direct)) {
                return;
            }
            boolean folder = slash >= 0 || entry.isDirectory();
            NodePath childPath = NodePath.inArchive(archiveFilePath, joinInner(innerDir, direct));
            nodes.add(new VirtualNode(childPath, folder,
                    folder ? -1L : entry.getSize(), entry.getTime(), this));
        });
        return nodes;
    }

    private static String normalizeEntryPrefix(String innerPath) {
        String stripped = stripLeadingSlash(innerPath);
        if (stripped.isEmpty()) {
            return "";
        }
        return stripped.endsWith("/") ? stripped : stripped + "/";
    }

    private static String stripLeadingSlash(String value) {
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private static String joinInner(String parent, String child) {
        return "/".equals(parent) ? "/" + child : parent + "/" + child;
    }

    private static final class ArchiveSession {
        final ZipFile zip;
        final long containerLength;
        final long containerModifiedAt;
        int activeUses;

        ArchiveSession(NodePath archiveFilePath) throws IOException {
            File physical = new File(archiveFilePath.path());
            this.zip = new ZipFile(physical);
            this.containerLength = physical.length();
            this.containerModifiedAt = physical.lastModified();
        }

        boolean matches(NodePath archiveFilePath) {
            File physical = new File(archiveFilePath.path());
            return physical.length() == containerLength
                    && physical.lastModified() == containerModifiedAt;
        }
    }

    private final class SessionInputStream extends FilterInputStream {
        private final ArchiveSession session;
        private boolean closed;

        SessionInputStream(InputStream input, ArchiveSession session) {
            super(input);
            this.session = session;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
            } finally {
                release(session);
            }
        }
    }

    private final class StagedEntryOutputStream extends FilterOutputStream {
        private final Path payload;
        private final NodePath archiveFilePath;
        private final String innerPath;
        private boolean closed;

        StagedEntryOutputStream(OutputStream output,
                                Path payload,
                                NodePath archiveFilePath,
                                String innerPath) {
            super(output);
            this.payload = payload;
            this.archiveFilePath = archiveFilePath;
            this.innerPath = innerPath;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
                mutate(archiveFilePath,
                        () -> mutationBackend.replaceFile(archiveFilePath, innerPath, payload));
            } catch (NodeException error) {
                throw new IOException(error.getMessage(), error);
            } finally {
                try {
                    Files.deleteIfExists(payload);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @FunctionalInterface
    private interface ArchiveMutation {
        void run() throws NodeException;
    }
}

package com.vpt.filemanager.node.source;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

/**
 * Read-only ZIP-backed virtual-node source.
 *
 * <p>ZIP central directories are cached to keep navigation responsive, but the cache is bounded.
 * An archive with an open entry stream is pinned until that stream closes; inactive least-recently
 * used sessions are then closed and evicted. Opening unrelated archives therefore cannot retain
 * every {@link ZipFile} for the process lifetime.
 */
@Singleton
public final class ArchiveSource implements NodeSource {
    static final int MAX_OPEN_ARCHIVES = 4;

    private final Map<NodePath, ArchiveSession> sessions =
            new LinkedHashMap<>(MAX_OPEN_ARCHIVES + 1, 0.75f, true);

    @Inject
    public ArchiveSource() {
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
        throw new NodeException("Archive write is not supported in v1");
    }

    @Override
    public boolean supportsWrite() {
        return false;
    }

    @Override
    public VirtualNode createFile(NodePath path) throws NodeException {
        throw new NodeException("Archive write is not supported in v1");
    }

    @Override
    public VirtualNode createFolder(NodePath path) throws NodeException {
        throw new NodeException("Archive write is not supported in v1");
    }

    @Override
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        throw new NodeException("Archive write is not supported in v1");
    }

    @Override
    public void delete(VirtualNode node) throws NodeException {
        throw new NodeException("Archive write is not supported in v1");
    }

    private synchronized ArchiveSession acquire(NodePath archiveFilePath) throws NodeException {
        ArchiveSession existing = sessions.get(archiveFilePath);
        if (existing != null) {
            existing.activeUses++;
            return existing;
        }
        try {
            ArchiveSession session = new ArchiveSession(new ZipFile(archiveFilePath.path()));
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
        int activeUses;

        ArchiveSession(ZipFile zip) {
            this.zip = zip;
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
}

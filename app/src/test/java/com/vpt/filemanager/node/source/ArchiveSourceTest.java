package com.vpt.filemanager.node.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

public final class ArchiveSourceTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void inactiveArchives_areEvictedAtBound() throws Exception {
        ArchiveSource source = new ArchiveSource();

        for (int index = 0; index < ArchiveSource.MAX_OPEN_ARCHIVES + 3; index++) {
            NodePath archive = createArchive("cache-" + index + ".zip", "entry.txt", "value");
            VirtualNode root = source.resolve(NodePath.inArchive(archive, "/"));
            source.list(root);
        }

        assertEquals(ArchiveSource.MAX_OPEN_ARCHIVES, source.cachedSessionCount());
    }

    @Test
    public void openEntryStream_remainsReadableWhileOtherArchivesEvict() throws Exception {
        ArchiveSource source = new ArchiveSource();
        NodePath pinnedArchive = createArchive("pinned.zip", "entry.txt", "pinned");
        VirtualNode pinnedEntry = source.resolve(NodePath.inArchive(pinnedArchive, "/entry.txt"));

        try (InputStream input = source.read(pinnedEntry)) {
            for (int index = 0; index < ArchiveSource.MAX_OPEN_ARCHIVES + 3; index++) {
                NodePath archive = createArchive("other-" + index + ".zip", "entry.txt", "value");
                source.list(source.resolve(NodePath.inArchive(archive, "/")));
            }

            assertEquals('p', input.read());
            assertTrue(source.cachedSessionCount() <= ArchiveSource.MAX_OPEN_ARCHIVES);
        }
    }

    @Test
    public void implicitDirectory_resolvesAsFolder() throws Exception {
        ArchiveSource source = new ArchiveSource();
        NodePath archive = createArchive("implicit.zip", "docs/note.txt", "hello");

        VirtualNode folder = source.resolve(NodePath.inArchive(archive, "/docs"));

        assertTrue(folder.isFolder());
        assertEquals("note.txt", folder.children().get(0).name());
    }

    @Test
    public void writableEntries_createWriteRenameAndDeleteThroughVirtualNodes() throws Exception {
        ArchiveSource source = new ArchiveSource();
        NodePath archive = createArchive("writable.zip", "kept.txt", "keep");
        VirtualNode root = source.resolve(NodePath.inArchive(archive, "/"));
        VirtualNode folder = source.createFolder(root.path().child("docs"));
        VirtualNode created = source.createFile(folder.path().child("note.txt"));

        try (java.io.OutputStream output = created.openWrite()) {
            output.write("edited".getBytes(StandardCharsets.UTF_8));
        }
        VirtualNode renamed = source.rename(source.resolve(created.path()), "renamed.txt");

        try (InputStream input = renamed.openRead()) {
            assertEquals("edited", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        source.delete(folder);

        assertThrows(com.vpt.filemanager.node.NodeException.class,
                () -> source.resolve(renamed.path()));
        try (InputStream input = source.read(source.resolve(
                NodePath.inArchive(archive, "/kept.txt")))) {
            assertEquals("keep", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void externalContainerRewrite_invalidatesInactiveZipSessionOnNextRead() throws Exception {
        ArchiveSource source = new ArchiveSource();
        NodePath archive = createArchive("external.zip", "old.txt", "old");
        VirtualNode root = source.resolve(NodePath.inArchive(archive, "/"));
        assertEquals("old.txt", source.list(root).get(0).name());

        Path physical = physicalPath(archive);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(physical))) {
            output.putNextEntry(new ZipEntry("new-longer-name.txt"));
            output.write("new-value".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Files.setLastModifiedTime(physical,
                FileTime.fromMillis(System.currentTimeMillis() + 2_000L));

        assertEquals("new-longer-name.txt", source.list(root).get(0).name());
    }

    private NodePath createArchive(String fileName, String entryName, String content)
            throws Exception {
        Path archive = temp.getRoot().toPath().resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(java.nio.file.Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return NodePath.local(archive.toString().replace('\\', '/'));
    }

    private static Path physicalPath(NodePath path) {
        String raw = path.path();
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                && raw.matches("^/[A-Za-z]:/.*")) {
            raw = raw.substring(1);
        }
        return java.nio.file.Paths.get(raw);
    }
}

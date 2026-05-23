package com.vpt.filemanager.node.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
}

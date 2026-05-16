package com.vpt.filemanager.data.fs.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vpt.filemanager.data.fs.DeleteOptions;
import com.vpt.filemanager.data.fs.ListOptions;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

public final class LocalFileSystemProviderTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final LocalFileSystemProvider provider = new LocalFileSystemProvider();

    @Test
    public void createListRenameDelete() throws Exception {
        Path root = tmp.newFolder("root").toPath();
        FilePath dir = FilePath.local(root.resolve("docs").toString());
        provider.createDirectory(dir);
        Files.write(root.resolve("docs/a.txt"), "hello".getBytes(StandardCharsets.UTF_8));

        List<FileNode> nodes = provider.list(dir, ListOptions.DEFAULT);
        assertEquals(1, nodes.size());
        assertEquals("a.txt", nodes.get(0).name());

        FilePath renamed = dir.child("b.txt");
        provider.rename(dir.child("a.txt"), renamed);
        assertTrue(provider.exists(renamed));
        assertFalse(provider.exists(dir.child("a.txt")));

        provider.delete(dir, DeleteOptions.PERMANENT);
        assertFalse(provider.exists(dir));
    }
}

package com.vpt.filemanager.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vpt.filemanager.browser.action.transfer.CancellationToken;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.item.ItemFactory;
import com.vpt.filemanager.data.local.LocalStorageAdapter;

public final class LocalStorageAdapterTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final LocalStorageAdapter files = new LocalStorageAdapter(new ItemFactory());

    @Test
    public void listRenameAndCopyOperateOnEphemeralItems() throws Exception {
        Path root = temporaryFolder.newFolder("root").toPath();
        Path source = Files.createDirectory(root.resolve("source"));
        Files.write(source.resolve("note.txt"), "note".getBytes(StandardCharsets.UTF_8));
        Item file = files.list(path(source)).get(0);
        assertEquals("note.txt", file.name());
        files.rename(file.localPath(), "renamed.txt");
        files.copy(path(source), path(root.resolve("copy")), new CancellationToken());
        assertTrue(Files.exists(root.resolve("copy/renamed.txt")));
    }

    private static String path(Path path) {
        return path.toString().replace('\\', '/');
    }
}

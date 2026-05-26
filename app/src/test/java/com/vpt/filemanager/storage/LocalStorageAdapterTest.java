package com.vpt.filemanager.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.vpt.filemanager.core.path.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public final class LocalStorageAdapterTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Test
    public void adapterReturnsRawFilesAndMutatesPhysicalStorage() throws Exception {
        File root = temporaryFolder.newFolder("root");
        LocalStorageAdapter storage = new LocalStorageAdapter(root);
        File source = storage.create(root, "source", true);
        Files.write(new File(source, "note.txt").toPath(), "text".getBytes(StandardCharsets.UTF_8));
        assertEquals("note.txt", storage.children(source).get(0).getName());
        storage.rename(storage.children(source).get(0), "renamed.txt");
        File renamed = new File(source, "renamed.txt");
        File copied = new File(root, "copy.txt");
        storage.copy(renamed, copied);
        assertTrue(copied.exists());
        assertEquals(source, storage.resolve(Path.storage("/source")));
        assertEquals(Path.storage("/source"), storage.pathOf(source));
    }

    @Test
    public void replaceMergesFoldersAndMoveDeletesSourceAfterCopy() throws Exception {
        File root = temporaryFolder.newFolder("replace-root");
        LocalStorageAdapter storage = new LocalStorageAdapter(root);
        File source = storage.create(root, "Docs-source", true);
        File destination = storage.create(root, "Docs", true);
        Files.write(new File(source, "same.txt").toPath(), "new".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(source, "added.txt").toPath(), "added".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(destination, "same.txt").toPath(), "old".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(destination, "kept.txt").toPath(), "kept".getBytes(StandardCharsets.UTF_8));

        storage.copyReplacing(source, destination);

        assertEquals("new", new String(Files.readAllBytes(new File(destination, "same.txt").toPath()),
                StandardCharsets.UTF_8));
        assertTrue(new File(destination, "added.txt").exists());
        assertTrue(new File(destination, "kept.txt").exists());
        assertTrue(source.exists());

        File moveSource = storage.create(root, "move.txt", false);
        Files.write(moveSource.toPath(), "moved".getBytes(StandardCharsets.UTF_8));
        File moveTarget = storage.create(root, "target.txt", false);
        Files.write(moveTarget.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        storage.moveReplacing(moveSource, moveTarget);
        assertFalse(moveSource.exists());
        assertEquals("moved", new String(Files.readAllBytes(moveTarget.toPath()),
                StandardCharsets.UTF_8));
    }
}

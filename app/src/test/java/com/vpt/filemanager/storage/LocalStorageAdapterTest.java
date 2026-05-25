package com.vpt.filemanager.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.vpt.filemanager.navigation.Location;

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
        assertEquals(source, storage.resolve(Location.storage("/source")));
        assertEquals(Location.storage("/source"), storage.locationOf(source));
    }
}

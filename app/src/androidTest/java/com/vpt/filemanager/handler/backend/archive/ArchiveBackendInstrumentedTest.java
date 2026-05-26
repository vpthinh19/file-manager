package com.vpt.filemanager.handler.backend.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.storage.LocalStorageAdapter;
@RunWith(AndroidJUnit4.class)
public final class ArchiveBackendInstrumentedTest {
    @Test
    public void zipSupportsBrowseMutateImportExtractAndSaveBack() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Path root = context.getCacheDir().toPath().resolve("archive-test");
        Files.createDirectories(root);
        Path zip = root.resolve("sample.zip");
        fixture(zip);

        LocalStorageAdapter storage = new LocalStorageAdapter(root.toFile());
        ArchiveBackend archives = new ArchiveBackend(storage);
        com.vpt.filemanager.core.path.Path location =
                com.vpt.filemanager.core.path.Path.archive("/sample.zip", "/");

        assertTrue(named(archives.list(location), "docs").isFolder());
        Entry hello = named(archives.list(location), "hello.txt");
        assertEquals("hello", read(Path.of(archives.materialize(hello))).trim());

        archives.create(location, "created.txt", false);
        Entry created = named(archives.list(location), "created.txt");
        archives.rename(created, "renamed.txt");
        assertTrue(archives.exists(location, "renamed.txt"));

        Path imported = root.resolve("imported.txt");
        Files.write(imported, "imported".getBytes(StandardCharsets.UTF_8));
        Entry physical = Entry.local(com.vpt.filemanager.core.path.Path.storage("/imported.txt"),
                path(imported), "imported.txt", false,
                Files.size(imported), Files.getLastModifiedTime(imported).toMillis());
        archives.importFromStorage(location, physical, physical.name(), false);
        assertTrue(archives.exists(location, "imported.txt"));

        Path edited = root.resolve("edited.txt");
        Files.write(edited, "saved through editor".getBytes(StandardCharsets.UTF_8));
        archives.updateFromMaterialized(
                com.vpt.filemanager.core.path.Path.archive("/sample.zip", "/hello.txt"),
                path(edited));
        Entry saved = named(archives.list(location), "hello.txt");
        assertEquals("saved through editor",
                read(Path.of(archives.materialize(saved))).trim());

        Path extracted = root.resolve("out.txt");
        archives.extractToStorage(named(archives.list(location), "imported.txt"), path(extracted));
        assertEquals("imported", read(extracted).trim());

        archives.delete(List.of(named(archives.list(location), "renamed.txt")));
        assertFalse(archives.exists(location, "renamed.txt"));

        Path secondZip = root.resolve("second.zip");
        fixture(secondZip);
        com.vpt.filemanager.core.path.Path second =
                com.vpt.filemanager.core.path.Path.archive("/second.zip", "/");
        archives.importFromArchive(second, named(archives.list(location), "docs"), "copied-docs", false);
        Entry nested = named(archives.list(
                com.vpt.filemanager.core.path.Path.archive("/second.zip", "/copied-docs")), "note.txt");
        assertEquals("note", read(Path.of(archives.materialize(nested))).trim());
    }

    @Test
    public void nestedZipWritesPropagateToOuterContainer() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Path root = context.getCacheDir().toPath().resolve("nested-archive-test");
        Files.createDirectories(root);
        Path outer = root.resolve("outer.zip");
        nestedFixture(outer);

        LocalStorageAdapter storage = new LocalStorageAdapter(root.toFile());
        ArchiveBackend archives = new ArchiveBackend(storage);
        com.vpt.filemanager.core.path.Path nestedRoot =
                com.vpt.filemanager.core.path.Path.archive("/outer.zip", "/inner.zip")
                        .mountArchive();

        assertEquals("inner", read(Path.of(
                archives.materialize(named(archives.list(nestedRoot), "inside.txt")))).trim());
        archives.create(nestedRoot, "created.txt", false);

        ArchiveBackend fresh = new ArchiveBackend(storage);
        assertTrue(fresh.exists(nestedRoot, "created.txt"));
    }

    private static Entry named(List<Entry> items, String name) {
        return items.stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow();
    }

    private static void fixture(Path zip) throws Exception {
        try (OutputStream output = Files.newOutputStream(zip);
             ZipOutputStream archive = new ZipOutputStream(output)) {
            archive.putNextEntry(new ZipEntry("docs/note.txt"));
            archive.write("note".getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
            archive.putNextEntry(new ZipEntry("hello.txt"));
            archive.write("hello".getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
    }

    private static void nestedFixture(Path outer) throws Exception {
        ByteArrayOutputStream innerBytes = new ByteArrayOutputStream();
        try (ZipOutputStream inner = new ZipOutputStream(innerBytes)) {
            inner.putNextEntry(new ZipEntry("inside.txt"));
            inner.write("inner".getBytes(StandardCharsets.UTF_8));
            inner.closeEntry();
        }
        try (OutputStream output = Files.newOutputStream(outer);
             ZipOutputStream archive = new ZipOutputStream(output)) {
            archive.putNextEntry(new ZipEntry("inner.zip"));
            archive.write(innerBytes.toByteArray());
            archive.closeEntry();
        }
    }

    private static String path(Path value) {
        return value.toString().replace('\\', '/');
    }

    private static String read(Path value) throws Exception {
        return new String(Files.readAllBytes(value), StandardCharsets.UTF_8);
    }
}

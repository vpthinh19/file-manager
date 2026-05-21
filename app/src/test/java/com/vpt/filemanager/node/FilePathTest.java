package com.vpt.filemanager.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FilePathTest {
    @Test
    public void localNormalizesAndNavigates() {
        FilePath path = FilePath.local("//sdcard//Download/");

        assertTrue(path.isLocal());
        assertEquals("/sdcard/Download", path.path());
        assertEquals("Download", path.name());
        assertEquals("/sdcard", path.parent().path());
        assertEquals("/sdcard/Download/file.txt", path.child("file.txt").path());
        assertEquals("txt", path.child("file.txt").extension());
    }

    @Test
    public void canonicalRoundTrip() {
        FilePath original = FilePath.local("/sdcard/My Folder/a.txt");

        assertEquals(original, FilePath.parse(original.toString()));
    }

    @Test
    public void archiveRoundTrip() {
        FilePath archive = FilePath.local("/sdcard/archive.zip");
        FilePath inner = FilePath.inArchive(archive, "/folder/a.txt");

        assertTrue(inner.isArchive());
        assertEquals(inner, FilePath.parse(inner.toString()));
        assertEquals(archive.toString(), inner.authority());
    }
}


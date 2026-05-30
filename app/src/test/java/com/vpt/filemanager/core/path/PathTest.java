package com.vpt.filemanager.core.path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PathTest {
    @Test
    public void archiveIsMountedUnderStorageAndRoundTrips() {
        Path archive = Path.archive("/Download/sample.zip", "/docs/readme.txt");
        assertEquals("storage:/Download/sample.zip!/docs/readme.txt",
                archive.serialize());
        assertEquals(archive, Path.parse(archive.serialize()));
        assertEquals(Path.archive("/Download/sample.zip", "/docs"),
                archive.parent());
        assertEquals(Path.storage("/Download"),
                Path.archive("/Download/sample.zip", "/").parent());
    }

    @Test
    public void nestedArchiveRoundTripsAndReturnsToContainingFolder() {
        Path nested = Path.archive("/Download/outer.zip", "/packages/inner.zip")
                .mountArchive()
                .child("readme.txt");
        assertEquals("storage:/Download/outer.zip!/packages/inner.zip!/readme.txt",
                nested.serialize());
        assertEquals(nested, Path.parse(nested.serialize()));
        assertEquals(Path.archive("/Download/outer.zip", "/packages"),
                Path.archive("/Download/outer.zip", "/packages/inner.zip").mountArchive().parent());
    }

    @Test
    public void publicRootsCannotNavigateAboveBoundary() {
        assertNull(Path.storageRoot().parent());
        assertNull(Path.trash().parent());
        assertNull(Path.bookmarks().parent());
        assertTrue(Path.storage("/Documents").parent().isStorageRoot());
    }

    @Test
    public void searchSerializesScopeAndQuery() {
        Path search = Path.search("/My Folder", "draft/report");
        assertEquals(search, Path.parse(search.serialize()));
    }
}

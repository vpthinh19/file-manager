package com.vpt.filemanager.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LocationTest {
    @Test
    public void archiveIsMountedUnderStorageAndRoundTrips() {
        Location archive = Location.archive("/storage/emulated/0/Download/sample.zip", "/docs/readme.txt");
        assertEquals("storage:/Download/sample.zip!/docs/readme.txt",
                archive.serialize());
        assertEquals(archive, Location.parse(archive.serialize()));
        assertEquals(Location.archive("/storage/emulated/0/Download/sample.zip", "/docs"),
                archive.parent());
        assertEquals(Location.storage("/storage/emulated/0/Download"),
                Location.archive("/storage/emulated/0/Download/sample.zip", "/").parent());
    }

    @Test
    public void publicRootsCannotNavigateAboveBoundary() {
        assertNull(Location.storageRoot().parent());
        assertNull(Location.trash().parent());
        assertNull(Location.bookmarks().parent());
        assertTrue(Location.storage("/storage/emulated/0/Documents").parent().isStorageRoot());
    }

    @Test
    public void searchSerializesScopeAndQuery() {
        Location search = Location.search("/storage/emulated/0/My Folder", "draft/report");
        assertEquals(search, Location.parse(search.serialize()));
    }
}

package com.vpt.filemanager.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LocationTest {
    @Test
    public void archiveIsMountedUnderStorageAndRoundTrips() {
        Location archive = Location.archive("/Download/sample.zip", "/docs/readme.txt");
        assertEquals("storage:/Download/sample.zip!/docs/readme.txt",
                archive.serialize());
        assertEquals(archive, Location.parse(archive.serialize()));
        assertEquals(Location.archive("/Download/sample.zip", "/docs"),
                archive.parent());
        assertEquals(Location.storage("/Download"),
                Location.archive("/Download/sample.zip", "/").parent());
    }

    @Test
    public void publicRootsCannotNavigateAboveBoundary() {
        assertNull(Location.storageRoot().parent());
        assertNull(Location.trash().parent());
        assertNull(Location.bookmarks().parent());
        assertTrue(Location.storage("/Documents").parent().isStorageRoot());
    }

    @Test
    public void searchSerializesScopeAndQuery() {
        Location search = Location.search("/My Folder", "draft/report");
        assertEquals(search, Location.parse(search.serialize()));
    }
}

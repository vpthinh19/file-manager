package com.vpt.filemanager.browser.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class PathTest {
    @Test
    public void typedLocationsRoundTripWithoutVirtualRoot() {
        Path storage = Path.storage("/storage/emulated/0/My Folder");
        Path search = Path.search(storage.directory(), "draft/report");
        Path archive = Path.archive("/storage/emulated/0/backups/data.zip", "/docs");
        assertEquals(storage, Path.deserialize(storage.serialize()));
        assertEquals(search, Path.deserialize(search.serialize()));
        assertEquals(archive, Path.deserialize(archive.serialize()));
        assertEquals(Path.archive(archive.container(), "/"), archive.parent());
        assertEquals(Path.storage("/storage/emulated/0/backups"),
                Path.archive(archive.container(), "/").parent());
        assertEquals(Path.trash(), Path.deserialize("trash"));
        assertEquals(Path.bookmarks(), Path.deserialize("bookmarks"));
        assertNull(Path.trash().parent());
        assertNull(Path.bookmarks().parent());
    }
}

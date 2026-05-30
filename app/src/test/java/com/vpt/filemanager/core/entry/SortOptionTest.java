package com.vpt.filemanager.core.entry;

import com.vpt.filemanager.core.path.Path;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class SortOptionTest {
    @Test
    public void parentAndFoldersRemainBeforeFiles() {
        List<Entry> entries = new ArrayList<>(List.of(
                Entry.local(Path.storage("/z.txt"), "/tmp/z.txt", "z.txt", false, 1, 0),
                Entry.local(Path.storage("/a"), "/tmp/a", "a", true, -1, 0),
                Entry.parent(Path.storageRoot())));
        entries.sort(SortOption.NAME_ASC.comparator());
        assertEquals("..", entries.get(0).name());
        assertEquals("a", entries.get(1).name());
        assertEquals("z.txt", entries.get(2).name());
    }
}

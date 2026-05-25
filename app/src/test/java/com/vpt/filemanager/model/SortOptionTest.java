package com.vpt.filemanager.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class SortOptionTest {
    @Test
    public void parentAndFoldersRemainBeforeFiles() {
        List<Entry> entries = new ArrayList<>(List.of(
                Entry.local(Location.storage("/storage/emulated/0/z.txt"), "z.txt", false, 1, 0),
                Entry.local(Location.storage("/storage/emulated/0/a"), "a", true, -1, 0),
                Entry.parent(Location.storageRoot())));
        entries.sort(SortOption.NAME_ASC.comparator());
        assertEquals("..", entries.get(0).name());
        assertEquals("a", entries.get(1).name());
        assertEquals("z.txt", entries.get(2).name());
    }
}

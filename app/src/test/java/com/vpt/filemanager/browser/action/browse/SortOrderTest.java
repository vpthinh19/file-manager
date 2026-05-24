package com.vpt.filemanager.browser.action.browse;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.ItemType;
import com.vpt.filemanager.browser.item.Item;

public final class SortOrderTest {
    @Test
    public void parentThenFolderThenFilesAreStableForRendering() {
        List<Item> items = new ArrayList<>(List.of(
                Item.local("/r/z.txt", "z.txt", false, 1, 0, ItemType.TEXT),
                Item.local("/r/a", "a", true, -1, 0, ItemType.FOLDER),
                Item.parent(Path.storage("/"))));
        items.sort(SortOrder.NAME_ASC.comparator());
        assertEquals("..", items.get(0).name());
        assertEquals("a", items.get(1).name());
        assertEquals("z.txt", items.get(2).name());
    }
}

package com.vpt.filemanager.browser.item;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ItemFactoryTest {
    private final ItemFactory factory = new ItemFactory();

    @Test
    public void classifiesSharedTapStrategiesWithoutHandlerInstancesOnRows() {
        assertEquals(ItemType.FOLDER, factory.local("/tmp/a", "a", true, -1, 0).behavior());
        assertEquals(ItemType.TEXT, factory.local("/tmp/a.kt", "a.kt", false, 1, 0).behavior());
        assertEquals(ItemType.IMAGE, factory.local("/tmp/a.jpg", "a.jpg", false, 1, 0).behavior());
        assertEquals(ItemType.ARCHIVE, factory.local("/tmp/a.zip", "a.zip", false, 1, 0).behavior());
        assertEquals(ItemType.ARCHIVE, factory.local("/tmp/a.cab", "a.cab", false, 1, 0).behavior());
        assertEquals(ItemType.ARCHIVE, factory.local("/tmp/a.tar.zst", "a.tar.zst", false, 1, 0).behavior());
        assertEquals(ItemType.PARENT, Item.parent(Path.storage("/tmp")).behavior());
    }
}

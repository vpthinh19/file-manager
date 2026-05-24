package com.vpt.filemanager.browser.rule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.action.browse.SortOrder;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.ItemType;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.browser.workspace.state.PaneState;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class RuleEngineTest {
    private final RuleEngine rules = new RuleEngine(new SelectionRule(), new LocationRule());
    private final Path left = Path.storage("/storage/emulated/0/left");
    private final Path right = Path.storage("/storage/emulated/0/right");

    @Test
    public void sameDestinationDisablesTransferAndFileCannotBeBookmarked() {
        Item file = Item.local(left.directory() + "/a.txt", "a.txt", false, 1, 1,
                ItemType.TEXT);
        WorkspaceSnapshot same = state(left, left, file);
        assertTrue(rules.disabled(same).contains(ActionKey.COPY));
        assertTrue(rules.disabled(same).contains(ActionKey.MOVE));
        assertTrue(rules.disabled(state(left, right, file)).contains(ActionKey.BOOKMARK));
    }

    @Test
    public void oneLocalFolderMayBeBookmarkedButTrashIsReadOnly() {
        Item folder = Item.local(left.directory() + "/docs", "docs", true, -1, 1,
                ItemType.FOLDER);
        assertFalse(rules.disabled(state(left, right, folder)).contains(ActionKey.BOOKMARK));
        assertTrue(rules.disabled(state(Path.trash(), right, folder)).contains(ActionKey.CREATE));
    }

    @Test
    public void writableArchiveEnablesLocalMutationButRarIsReadOnly() {
        Path zip = Path.archive("/storage/emulated/0/data.zip", "/");
        Path rar = Path.archive("/storage/emulated/0/data.rar", "/");
        Item zipEntry = Item.archive(Path.archive(zip.container(), "/note.txt"), "note.txt",
                false, 1, 1, ItemType.TEXT);
        assertFalse(rules.disabled(state(zip, right, zipEntry)).contains(ActionKey.RENAME));
        assertFalse(rules.disabled(state(zip, right, zipEntry)).contains(ActionKey.DELETE));
        assertFalse(rules.disabled(state(zip, right, zipEntry)).contains(ActionKey.COPY));
        assertFalse(rules.disabled(state(zip, right, zipEntry)).contains(ActionKey.OPEN_WITH));
        assertFalse(rules.disabled(state(zip, right, zipEntry)).contains(ActionKey.SHARE));
        assertTrue(rules.disabled(state(zip, right, zipEntry)).contains(ActionKey.PROPERTIES));
        assertTrue(rules.disabled(state(rar, right, zipEntry)).contains(ActionKey.DELETE));
    }

    private static WorkspaceSnapshot state(Path active, Path inactive, Item item) {
        PaneState left = new PaneState(active, List.of(item), Set.of(item.key()), SortOrder.DEFAULT,
                false, null, true, false, false, 0, 1, 0, 0);
        PaneState right = new PaneState(inactive, List.of(), Set.of(), SortOrder.DEFAULT,
                false, null, false, false, false, 0, 0, 0, 0);
        return new WorkspaceSnapshot(left, right, PaneId.LEFT);
    }
}

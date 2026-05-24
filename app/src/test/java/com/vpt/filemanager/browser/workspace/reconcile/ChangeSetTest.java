package com.vpt.filemanager.browser.workspace.reconcile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import com.vpt.filemanager.browser.item.Path;

public final class ChangeSetTest {
    @Test
    public void folderMutationInvalidatesBothVisibleFolderAndSearchScope() {
        ChangeSet changed = new ChangeSet(
                Set.of(Path.storage("/storage/emulated/0/Documents")), Set.of());
        assertTrue(changed.affects(Path.storage("/storage/emulated/0/Documents")));
        assertTrue(changed.affects(Path.search("/storage/emulated/0", "report")));
        assertFalse(changed.affects(Path.storage("/storage/emulated/0/Pictures")));
    }

    @Test
    public void removedAncestorInvalidatesVisibleDescendant() {
        ChangeSet removed = new ChangeSet(Set.of(), Set.of("/storage/emulated/0/Documents"));
        assertTrue(removed.affects(Path.storage("/storage/emulated/0/Documents/project")));
    }
}

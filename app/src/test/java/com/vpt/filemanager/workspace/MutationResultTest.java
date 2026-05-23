package com.vpt.filemanager.workspace;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.vpt.filemanager.node.NodePath;

public final class MutationResultTest {
    @Test
    public void changedContainer_onlyInvalidatesMatchingVisibleListing() {
        MutationResult mutation = MutationResult.builder()
                .changedContainer(NodePath.local("/sdcard/Documents"))
                .build();

        assertTrue(mutation.affectsListing(NodePath.local("/sdcard/Documents")));
        assertFalse(mutation.affectsListing(NodePath.local("/sdcard/Pictures")));
    }

    @Test
    public void removedSubtree_invalidatesPaneInsideRemovedFolder() {
        MutationResult mutation = MutationResult.builder()
                .removedSubtree(NodePath.local("/sdcard/Documents"))
                .build();

        assertTrue(mutation.affectsListing(NodePath.local("/sdcard/Documents/project")));
        assertFalse(mutation.affectsListing(NodePath.local("/sdcard/Download")));
    }

    @Test
    public void allLiveSnapshots_isExplicitFallbackForUnknownScope() {
        MutationResult mutation = MutationResult.allLiveSnapshots();

        assertTrue(mutation.affectsListing(NodePath.TRASH_ROOT));
        assertTrue(mutation.affectsListing(NodePath.local("/sdcard/Documents")));
    }

    @Test
    public void changedParentOrRemovedSubtree_invalidatesOpenDocument() {
        NodePath document = NodePath.local("/sdcard/Documents/note.txt");

        assertTrue(MutationResult.builder()
                .changedContainer(document.parent())
                .build()
                .affectsNode(document));
        assertTrue(MutationResult.builder()
                .removedSubtree(NodePath.local("/sdcard/Documents"))
                .build()
                .affectsNode(document));
        assertFalse(MutationResult.builder()
                .changedContainer(NodePath.local("/sdcard/Pictures"))
                .build()
                .affectsNode(document));
    }
}

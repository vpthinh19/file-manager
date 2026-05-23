package com.vpt.filemanager.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.FileObserver;
import android.os.Looper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import com.vpt.filemanager.node.NodePath;

@RunWith(RobolectricTestRunner.class)
public final class WorkspaceFileWatcherTest {
    @Test
    public void filesystemBurst_isPublishedAsOneMutation() {
        NodePath documents = NodePath.local("/sdcard/Documents");
        WorkspaceFileWatcher watcher = new WorkspaceFileWatcher();
        List<MutationResult> mutations = new ArrayList<>();
        watcher.setListener(mutations::add);

        watcher.onFilesystemEvent(documents, FileObserver.CREATE);
        watcher.onFilesystemEvent(documents, FileObserver.CLOSE_WRITE);
        watcher.onFilesystemEvent(documents, FileObserver.DELETE);

        assertTrue(mutations.isEmpty());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                Duration.ofMillis(WorkspaceFileWatcher.EVENT_COALESCE_MILLIS + 1));

        assertEquals(1, mutations.size());
        assertTrue(mutations.get(0).changedContainers.contains(documents));
    }

    @Test
    public void removedObservedDirectory_preservesRemovedSubtreeSignal() {
        NodePath documents = NodePath.local("/sdcard/Documents");
        WorkspaceFileWatcher watcher = new WorkspaceFileWatcher();
        List<MutationResult> mutations = new ArrayList<>();
        watcher.setListener(mutations::add);

        watcher.onFilesystemEvent(documents, FileObserver.DELETE_SELF);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                Duration.ofMillis(WorkspaceFileWatcher.EVENT_COALESCE_MILLIS + 1));

        assertEquals(1, mutations.size());
        assertTrue(mutations.get(0).removedSubtrees.contains(documents));
    }
}

package com.vpt.filemanager.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NodePathTest {
    @Test
    public void localNormalizesAndNavigates() {
        NodePath path = NodePath.local("//sdcard//Download/");

        assertTrue(path.isLocal());
        assertEquals("/sdcard/Download", path.path());
        assertEquals("Download", path.name());
        assertEquals("/sdcard", path.parent().path());
        assertEquals("/sdcard/Download/file.txt", path.child("file.txt").path());
        assertEquals("txt", path.child("file.txt").extension());
    }

    @Test
    public void canonicalRoundTrip() {
        NodePath original = NodePath.local("/sdcard/My Folder/a.txt");

        assertEquals(original, NodePath.parse(original.toString()));
    }

    @Test
    public void archiveRoundTrip() {
        NodePath archive = NodePath.local("/sdcard/archive.zip");
        NodePath inner = NodePath.inArchive(archive, "/folder/a.txt");

        assertTrue(inner.isArchive());
        assertEquals(inner, NodePath.parse(inner.toString()));
        assertEquals(archive.toString(), inner.authority());
    }

    @Test
    public void virtualRootRoundTrip() {
        assertTrue(NodePath.ROOT.isRoot());
        assertEquals(NodePath.ROOT, NodePath.parse(NodePath.ROOT.toString()));
    }

    @Test
    public void descendantCheck_requiresSameVirtualSourceAndSegmentBoundary() {
        NodePath parent = NodePath.local("/sdcard/Documents");

        assertTrue(NodePath.local("/sdcard/Documents/report.txt").isSameOrDescendantOf(parent));
        assertTrue(parent.isSameOrDescendantOf(parent));
        assertTrue(!NodePath.local("/sdcard/Documents-old").isSameOrDescendantOf(parent));
        assertTrue(!NodePath.inArchive(NodePath.local("/sdcard/Documents/a.zip"), "/report.txt")
                .isSameOrDescendantOf(parent));
    }
}


package com.vpt.filemanager.browser.constraint;

import static com.vpt.filemanager.browser.NodeActionsBottomSheet.Action.BOOKMARK;
import static com.vpt.filemanager.browser.NodeActionsBottomSheet.Action.COMPRESS;
import static com.vpt.filemanager.browser.NodeActionsBottomSheet.Action.COPY;
import static com.vpt.filemanager.browser.NodeActionsBottomSheet.Action.DELETE;
import static com.vpt.filemanager.browser.NodeActionsBottomSheet.Action.MOVE;
import static com.vpt.filemanager.browser.NodeActionsBottomSheet.Action.OPEN_WITH;
import static com.vpt.filemanager.browser.NodeActionsBottomSheet.Action.PROPERTIES;
import static com.vpt.filemanager.browser.NodeActionsBottomSheet.Action.RENAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;

import com.vpt.filemanager.browser.NodeActionsBottomSheet.Action;
import com.vpt.filemanager.node.FilePath;

public final class ActionConstraintsTest {
    private static final FilePath FOO = FilePath.local("/sdcard/foo.txt");
    private static final FilePath BAR = FilePath.local("/sdcard/bar.txt");
    private static final FilePath BAZ_DIR = FilePath.local("/sdcard/baz");
    private static final FilePath DIR_A = FilePath.local("/sdcard/A");
    private static final FilePath DIR_B = FilePath.local("/sdcard/B");
    private static final FilePath ARCHIVE_ENTRY =
            FilePath.inArchive(FilePath.local("/sdcard/x.zip"), "/inner/a.txt");

    private static Set<FilePath> setOf(FilePath... paths) {
        Set<FilePath> s = new LinkedHashSet<>();
        for (FilePath p : paths) s.add(p);
        return s;
    }

    @Test
    public void singleFile_disablesBookmark_keepsRenameAndOpenWith() {
        WorkspaceState state = WorkspaceState.of(setOf(FOO), Boolean.FALSE, DIR_A, DIR_B);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertTrue(disabled.contains(BOOKMARK));
        assertFalse(disabled.contains(RENAME));
        assertFalse(disabled.contains(OPEN_WITH));
        assertFalse(disabled.contains(PROPERTIES));
    }

    @Test
    public void singleFolder_disablesOpenWith_keepsBookmark() {
        WorkspaceState state = WorkspaceState.of(setOf(BAZ_DIR), Boolean.TRUE, DIR_A, DIR_B);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertTrue(disabled.contains(OPEN_WITH));
        assertFalse(disabled.contains(BOOKMARK));
        assertFalse(disabled.contains(RENAME));
    }

    @Test
    public void multiSelect_disablesAllSingleTargetActions() {
        WorkspaceState state = WorkspaceState.of(setOf(FOO, BAR), null, DIR_A, DIR_B);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertTrue(disabled.contains(RENAME));
        assertTrue(disabled.contains(PROPERTIES));
        assertTrue(disabled.contains(OPEN_WITH));
        assertTrue(disabled.contains(BOOKMARK));
        // batch-safe ones still enabled
        assertFalse(disabled.contains(COPY));
        assertFalse(disabled.contains(MOVE));
        assertFalse(disabled.contains(DELETE));
        assertFalse(disabled.contains(COMPRESS));
    }

    @Test
    public void archiveEntry_disablesReadOnlyAndBookmarkAndOpenWith() {
        WorkspaceState state = WorkspaceState.of(
                setOf(ARCHIVE_ENTRY), Boolean.FALSE, DIR_A, DIR_B);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertTrue(disabled.contains(RENAME));
        assertTrue(disabled.contains(DELETE));
        assertTrue(disabled.contains(MOVE));
        assertTrue(disabled.contains(COMPRESS));
        assertTrue(disabled.contains(BOOKMARK));
        // Rule 6: archive is non-local → OPEN_WITH disabled (silent no-op without this).
        assertTrue(disabled.contains(OPEN_WITH));
        // COPY still enabled — extract from archive is a legitimate cross-source op.
        assertFalse(disabled.contains(COPY));
    }

    @Test
    public void nonLocalEntry_disablesOpenWith() {
        // Bookmark scheme — different non-local source (not archive). Should still disable OPEN_WITH.
        FilePath bookmarkEntry = FilePath.parse("bookmark://uuid-1/My Folder");
        WorkspaceState state = WorkspaceState.of(
                setOf(bookmarkEntry), Boolean.TRUE, DIR_A, DIR_B);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertTrue(disabled.contains(OPEN_WITH));
        // Archive-specific rules should NOT fire for non-archive non-local entries.
        assertFalse(disabled.contains(DELETE));
        assertFalse(disabled.contains(MOVE));
    }

    @Test
    public void samePathBothPanes_disablesCopyAndMove() {
        WorkspaceState state = WorkspaceState.of(setOf(FOO), Boolean.FALSE, DIR_A, DIR_A);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertTrue(disabled.contains(COPY));
        assertTrue(disabled.contains(MOVE));
    }

    @Test
    public void differentPathsInPanes_keepsCopyAndMove() {
        WorkspaceState state = WorkspaceState.of(setOf(FOO), Boolean.FALSE, DIR_A, DIR_B);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertFalse(disabled.contains(COPY));
        assertFalse(disabled.contains(MOVE));
    }

    @Test
    public void nullInactivePath_keepsCopyAndMove() {
        WorkspaceState state = WorkspaceState.of(setOf(FOO), Boolean.FALSE, DIR_A, null);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertFalse(disabled.contains(COPY));
        assertFalse(disabled.contains(MOVE));
    }

    @Test
    public void mixedSelectionWithArchive_unionsAllRules() {
        // multi-select + contains archive entry: union of (multi rules) + (archive rules).
        WorkspaceState state = WorkspaceState.of(
                setOf(FOO, ARCHIVE_ENTRY), null, DIR_A, DIR_B);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        // From multi-select
        assertTrue(disabled.contains(RENAME));
        assertTrue(disabled.contains(PROPERTIES));
        assertTrue(disabled.contains(OPEN_WITH));
        assertTrue(disabled.contains(BOOKMARK));
        // From archive entry
        assertTrue(disabled.contains(DELETE));
        assertTrue(disabled.contains(MOVE));
        assertTrue(disabled.contains(COMPRESS));
    }

    @Test
    public void singleSelectionUnknownType_appliesArchiveRuleIfApplicable() {
        // singleIsFolder == null but selection contains archive entry → archive rule still fires.
        WorkspaceState state = WorkspaceState.of(
                setOf(ARCHIVE_ENTRY), null, DIR_A, DIR_B);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertTrue(disabled.contains(RENAME));
        assertTrue(disabled.contains(BOOKMARK));
    }

    @Test
    public void emptySelection_returnsEmptyDisabledSet() {
        // Degenerate input — caller (controller) guards before calling, but compute is defensive.
        WorkspaceState state = WorkspaceState.of(setOf(), null, DIR_A, DIR_B);

        EnumSet<Action> disabled = ActionConstraints.compute(state);

        assertEquals(EnumSet.noneOf(Action.class), disabled);
    }
}

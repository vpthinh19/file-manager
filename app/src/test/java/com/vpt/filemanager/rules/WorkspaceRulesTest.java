package com.vpt.filemanager.rules;

import static com.vpt.filemanager.workspace.WorkspaceAction.BOOKMARK;
import static com.vpt.filemanager.workspace.WorkspaceAction.COMPRESS;
import static com.vpt.filemanager.workspace.WorkspaceAction.COPY;
import static com.vpt.filemanager.workspace.WorkspaceAction.DELETE;
import static com.vpt.filemanager.workspace.WorkspaceAction.MOVE;
import static com.vpt.filemanager.workspace.WorkspaceAction.OPEN_WITH;
import static com.vpt.filemanager.workspace.WorkspaceAction.PROPERTIES;
import static com.vpt.filemanager.workspace.WorkspaceAction.RENAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.workspace.WorkspaceAction;

public final class WorkspaceRulesTest {
    private static final NodePath FOO = NodePath.local("/sdcard/foo.txt");
    private static final NodePath BAR = NodePath.local("/sdcard/bar.txt");
    private static final NodePath BAZ_DIR = NodePath.local("/sdcard/baz");
    private static final NodePath DIR_A = NodePath.local("/sdcard/A");
    private static final NodePath DIR_B = NodePath.local("/sdcard/B");
    private static final NodePath ARCHIVE_ENTRY =
            NodePath.inArchive(NodePath.local("/sdcard/x.zip"), "/inner/a.txt");

    private static Set<NodePath> setOf(NodePath... paths) {
        Set<NodePath> s = new LinkedHashSet<>();
        for (NodePath p : paths) s.add(p);
        return s;
    }

    @Test
    public void singleFile_disablesBookmark_keepsRenameAndOpenWith() {
        WorkspaceRuleState state = WorkspaceRuleState.of(setOf(FOO), Boolean.FALSE, DIR_A, DIR_B);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

        assertTrue(disabled.contains(BOOKMARK));
        assertFalse(disabled.contains(RENAME));
        assertFalse(disabled.contains(OPEN_WITH));
        assertFalse(disabled.contains(PROPERTIES));
    }

    @Test
    public void singleFolder_disablesOpenWith_keepsBookmark() {
        WorkspaceRuleState state = WorkspaceRuleState.of(setOf(BAZ_DIR), Boolean.TRUE, DIR_A, DIR_B);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

        assertTrue(disabled.contains(OPEN_WITH));
        assertFalse(disabled.contains(BOOKMARK));
        assertFalse(disabled.contains(RENAME));
    }

    @Test
    public void multiSelect_disablesAllSingleTargetActions() {
        WorkspaceRuleState state = WorkspaceRuleState.of(setOf(FOO, BAR), null, DIR_A, DIR_B);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

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
        WorkspaceRuleState state = WorkspaceRuleState.of(
                setOf(ARCHIVE_ENTRY), Boolean.FALSE, DIR_A, DIR_B);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

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
        NodePath bookmarkEntry = NodePath.parse("bookmark://uuid-1/My Folder");
        WorkspaceRuleState state = WorkspaceRuleState.of(
                setOf(bookmarkEntry), Boolean.TRUE, DIR_A, DIR_B);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

        assertTrue(disabled.contains(OPEN_WITH));
        // Archive-specific rules should NOT fire for non-archive non-local entries.
        assertFalse(disabled.contains(DELETE));
        assertFalse(disabled.contains(MOVE));
    }

    @Test
    public void samePathBothPanes_disablesCopyAndMove() {
        WorkspaceRuleState state = WorkspaceRuleState.of(setOf(FOO), Boolean.FALSE, DIR_A, DIR_A);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

        assertTrue(disabled.contains(COPY));
        assertTrue(disabled.contains(MOVE));
    }

    @Test
    public void differentPathsInPanes_keepsCopyAndMove() {
        WorkspaceRuleState state = WorkspaceRuleState.of(setOf(FOO), Boolean.FALSE, DIR_A, DIR_B);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

        assertFalse(disabled.contains(COPY));
        assertFalse(disabled.contains(MOVE));
    }

    @Test
    public void nullInactivePath_keepsCopyAndMove() {
        WorkspaceRuleState state = WorkspaceRuleState.of(setOf(FOO), Boolean.FALSE, DIR_A, null);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

        assertFalse(disabled.contains(COPY));
        assertFalse(disabled.contains(MOVE));
    }

    @Test
    public void mixedSelectionWithArchive_unionsAllRules() {
        // multi-select + contains archive entry: union of (multi rules) + (archive rules).
        WorkspaceRuleState state = WorkspaceRuleState.of(
                setOf(FOO, ARCHIVE_ENTRY), null, DIR_A, DIR_B);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

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
        WorkspaceRuleState state = WorkspaceRuleState.of(
                setOf(ARCHIVE_ENTRY), null, DIR_A, DIR_B);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

        assertTrue(disabled.contains(RENAME));
        assertTrue(disabled.contains(BOOKMARK));
    }

    @Test
    public void emptySelection_returnsEmptyDisabledSet() {
        // Degenerate input — caller (controller) guards before calling, but compute is defensive.
        WorkspaceRuleState state = WorkspaceRuleState.of(setOf(), null, DIR_A, DIR_B);

        EnumSet<WorkspaceAction> disabled = WorkspaceRules.compute(state);

        assertEquals(EnumSet.noneOf(WorkspaceAction.class), disabled);
    }
}

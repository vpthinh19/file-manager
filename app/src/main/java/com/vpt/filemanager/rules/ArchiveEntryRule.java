package com.vpt.filemanager.rules;

import java.util.EnumSet;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.workspace.WorkspaceAction;

/**
 * Capabilities specific to entries inside an archive.
 *
 * <p>Archive entries are mutable virtual nodes. They still cannot be bookmarked because
 * bookmarks identify persistent user locations, and compression of a selection inside an
 * existing container is not an implemented operation.
 */
final class ArchiveEntryRule implements WorkspaceRule {
    @Override
    public void apply(WorkspaceRuleState state, EnumSet<WorkspaceAction> disabled) {
        for (NodePath path : state.selection) {
            if (path.isArchive()) {
                disabled.add(WorkspaceAction.COMPRESS);
                disabled.add(WorkspaceAction.BOOKMARK);
            }
        }
    }
}

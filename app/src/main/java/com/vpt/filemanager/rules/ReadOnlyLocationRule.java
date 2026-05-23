package com.vpt.filemanager.rules;

import java.util.EnumSet;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.workspace.WorkspaceAction;

/**
 * Applies capabilities imposed by the active container and the cross-pane destination.
 */
final class ReadOnlyLocationRule implements WorkspaceRule {
    @Override
    public void apply(WorkspaceRuleState state, EnumSet<WorkspaceAction> disabled) {
        if (isReadOnlyContainer(state.activePath)) {
            disabled.add(WorkspaceAction.CREATE);
        }
        if (isReadOnlyContainer(state.inactivePath)) {
            disabled.add(WorkspaceAction.COPY);
            disabled.add(WorkspaceAction.MOVE);
        }
    }

    private static boolean isReadOnlyContainer(NodePath path) {
        return path != null && (path.isRoot() || path.isTrash() || path.isBookmark()
                || path.isSearch());
    }
}

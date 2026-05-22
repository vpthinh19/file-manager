package com.vpt.filemanager.rules;

import java.util.EnumSet;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.workspace.WorkspaceAction;

final class ArchiveReadOnlyRule implements WorkspaceRule {
    @Override
    public void apply(WorkspaceRuleState state, EnumSet<WorkspaceAction> disabled) {
        for (NodePath path : state.selection) {
            if (!path.isArchive()) {
                continue;
            }
            disabled.add(WorkspaceAction.RENAME);
            disabled.add(WorkspaceAction.DELETE);
            disabled.add(WorkspaceAction.MOVE);
            disabled.add(WorkspaceAction.COMPRESS);
            disabled.add(WorkspaceAction.BOOKMARK);
        }
    }
}

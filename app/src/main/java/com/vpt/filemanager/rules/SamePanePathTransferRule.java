package com.vpt.filemanager.rules;

import java.util.EnumSet;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.workspace.WorkspaceAction;

final class SamePanePathTransferRule implements WorkspaceRule {
    @Override
    public void apply(WorkspaceRuleState state, EnumSet<WorkspaceAction> disabled) {
        if (state.activePath != null && state.activePath.equals(state.inactivePath)) {
            disabled.add(WorkspaceAction.COPY);
            disabled.add(WorkspaceAction.MOVE);
            return;
        }
        if (state.activePath == null || !state.activePath.isSearch() || state.inactivePath == null) {
            return;
        }
        for (NodePath selected : state.selection) {
            if (selected.parent().equals(state.inactivePath)) {
                disabled.add(WorkspaceAction.COPY);
                disabled.add(WorkspaceAction.MOVE);
                return;
            }
        }
    }
}

package com.vpt.filemanager.rules;

import java.util.EnumSet;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.workspace.WorkspaceAction;

final class NonLocalOpenWithRule implements WorkspaceRule {
    @Override
    public void apply(WorkspaceRuleState state, EnumSet<WorkspaceAction> disabled) {
        for (NodePath path : state.selection) {
            if (!path.isLocal()) {
                disabled.add(WorkspaceAction.OPEN_WITH);
                return;
            }
        }
    }
}

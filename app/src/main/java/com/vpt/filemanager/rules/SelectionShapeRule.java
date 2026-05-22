package com.vpt.filemanager.rules;

import java.util.EnumSet;

import com.vpt.filemanager.workspace.WorkspaceAction;

final class SelectionShapeRule implements WorkspaceRule {
    @Override
    public void apply(WorkspaceRuleState state, EnumSet<WorkspaceAction> disabled) {
        if (state.selection.size() > 1) {
            disabled.add(WorkspaceAction.RENAME);
            disabled.add(WorkspaceAction.PROPERTIES);
            disabled.add(WorkspaceAction.OPEN_WITH);
            disabled.add(WorkspaceAction.BOOKMARK);
            return;
        }
        if (state.singleIsFolder == null) {
            return;
        }
        if (state.singleIsFolder) {
            disabled.add(WorkspaceAction.OPEN_WITH);
        } else {
            disabled.add(WorkspaceAction.BOOKMARK);
        }
    }
}

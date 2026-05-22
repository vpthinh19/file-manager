package com.vpt.filemanager.rules;

import java.util.EnumSet;

import com.vpt.filemanager.workspace.WorkspaceAction;

/**
 * A single workspace rule that can disable actions for the current state snapshot.
 */
public interface WorkspaceRule {
    void apply(WorkspaceRuleState state, EnumSet<WorkspaceAction> disabled);
}

package com.vpt.filemanager.rules;

import java.util.EnumSet;

import com.vpt.filemanager.workspace.WorkspaceAction;

/**
 * Backward-compatible facade for default workspace action constraints.
 *
 * <p>New code should use {@link RuleEngine} directly when it needs a custom rule set.
 */
public final class WorkspaceRules {
    private static final RuleEngine DEFAULT_ENGINE = RuleEngine.defaults();

    private WorkspaceRules() {
    }

    public static EnumSet<WorkspaceAction> compute(WorkspaceRuleState state) {
        return DEFAULT_ENGINE.disabledActions(state);
    }
}

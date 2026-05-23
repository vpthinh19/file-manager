package com.vpt.filemanager.rules;

import java.util.EnumSet;
import java.util.List;

import com.vpt.filemanager.workspace.WorkspaceAction;

/**
 * Composes workspace constraint rules into an action availability result.
 */
public final class RuleEngine {
    private static final List<WorkspaceRule> DEFAULT_RULES = List.of(
            new SelectionShapeRule(),
            new ArchiveEntryRule(),
            new NonLocalOpenWithRule(),
            new SamePanePathTransferRule(),
            new ReadOnlyLocationRule());

    private final List<WorkspaceRule> rules;

    public RuleEngine(List<WorkspaceRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static RuleEngine defaults() {
        return new RuleEngine(DEFAULT_RULES);
    }

    public EnumSet<WorkspaceAction> disabledActions(WorkspaceRuleState state) {
        EnumSet<WorkspaceAction> disabled = EnumSet.noneOf(WorkspaceAction.class);
        for (WorkspaceRule rule : rules) {
            rule.apply(state, disabled);
        }
        return disabled;
    }
}

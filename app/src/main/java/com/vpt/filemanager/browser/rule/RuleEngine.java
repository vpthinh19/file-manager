package com.vpt.filemanager.browser.rule;

import androidx.annotation.NonNull;

import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

/** Computes controls from external workspace state; widgets never own these constraints. */
@Singleton
public final class RuleEngine {
    private final List<Rule> rules;

    @Inject
    public RuleEngine(SelectionRule selectionRule, LocationRule locationRule) {
        rules = List.of(selectionRule, locationRule);
    }

    @NonNull
    public EnumSet<ActionKey> disabled(@NonNull WorkspaceSnapshot state) {
        EnumSet<ActionKey> disabled = EnumSet.noneOf(ActionKey.class);
        RuleContext context = RuleContext.from(state);
        for (Rule rule : rules) rule.disabledActions(context, disabled);
        return disabled;
    }

    public boolean allows(ActionKey key, WorkspaceSnapshot state) {
        return !disabled(state).contains(key);
    }
}

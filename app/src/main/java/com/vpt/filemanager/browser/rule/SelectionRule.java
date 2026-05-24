package com.vpt.filemanager.browser.rule;

import java.util.EnumSet;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.item.Item;

public final class SelectionRule implements Rule {
    @Inject public SelectionRule() {}

    @Override
    public void disabledActions(RuleContext state, EnumSet<ActionKey> disabled) {
        if (state.selection.isEmpty()) {
            disabled.addAll(EnumSet.of(ActionKey.COPY, ActionKey.MOVE, ActionKey.DELETE,
                    ActionKey.RENAME, ActionKey.PROPERTIES, ActionKey.SHARE,
                    ActionKey.OPEN_WITH, ActionKey.BOOKMARK));
            return;
        }
        if (state.selection.size() > 1) {
            disabled.addAll(EnumSet.of(ActionKey.RENAME, ActionKey.PROPERTIES,
                    ActionKey.OPEN_WITH, ActionKey.BOOKMARK));
            return;
        }
        Item only = state.selection.get(0);
        if (only.isFolder()) disabled.add(ActionKey.OPEN_WITH);
        if (!only.isFolder() || !only.isLocalActionTarget()) disabled.add(ActionKey.BOOKMARK);
        if (only.isArchiveEntry()) {
            disabled.addAll(EnumSet.of(ActionKey.BOOKMARK, ActionKey.PROPERTIES,
                    ActionKey.OPEN_WITH));
            if (!only.isFolder()) disabled.remove(ActionKey.OPEN_WITH);
        }
    }
}

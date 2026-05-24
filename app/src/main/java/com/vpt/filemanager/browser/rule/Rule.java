package com.vpt.filemanager.browser.rule;

import java.util.EnumSet;

import com.vpt.filemanager.browser.action.ActionKey;

public interface Rule {
    void disabledActions(RuleContext context, EnumSet<ActionKey> disabled);
}

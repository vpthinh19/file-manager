package com.vpt.filemanager.browser.rule;

import java.util.EnumSet;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.data.archive.ArchiveFormat;

public final class LocationRule implements Rule {
    @Inject public LocationRule() {}

    @Override
    public void disabledActions(RuleContext state, EnumSet<ActionKey> disabled) {
        boolean archiveWritable = state.active.isArchive()
                && ArchiveFormat.isWritable(state.active.container());
        if (!state.active.isStorage() && !archiveWritable) {
            disabled.addAll(EnumSet.of(ActionKey.CREATE, ActionKey.RENAME,
                    ActionKey.DELETE, ActionKey.MOVE));
        }
        if (state.active.isTrash()) {
            disabled.addAll(EnumSet.of(ActionKey.COPY, ActionKey.SHARE,
                    ActionKey.OPEN_WITH, ActionKey.BOOKMARK, ActionKey.PROPERTIES));
        }
        if (!state.active.isTrash()) {
            disabled.add(ActionKey.RESTORE_TRASH);
            disabled.add(ActionKey.EMPTY_TRASH);
        }
        if (state.active.isBookmarks()) {
            disabled.add(ActionKey.BOOKMARK);
        } else {
            disabled.add(ActionKey.REMOVE_BOOKMARK);
        }
        boolean writableDestination = state.inactive.isStorage() || state.inactive.isArchive()
                && ArchiveFormat.isWritable(state.inactive.container());
        boolean archiveToArchive = state.active.isArchive() && state.inactive.isArchive();
        if (!writableDestination || archiveToArchive || state.active.equals(state.inactive)) {
            disabled.add(ActionKey.COPY);
            disabled.add(ActionKey.MOVE);
        }
        if (state.active.isArchive() && !archiveWritable) disabled.add(ActionKey.MOVE);
    }
}

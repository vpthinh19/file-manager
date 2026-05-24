package com.vpt.filemanager.browser.action.transfer;

import java.util.List;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public record TransferEntriesAction(PaneId sourcePane, PaneId destinationPane,
                                    List<Item> items, TransferMode mode,
                                    TransferConflictResolver conflicts, CancellationToken token)
        implements Action {
    public TransferEntriesAction {
        items = List.copyOf(items);
    }
    @Override public ActionKey key() { return mode == TransferMode.COPY ? ActionKey.COPY : ActionKey.MOVE; }
}

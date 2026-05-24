package com.vpt.filemanager.browser.action;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.action.browse.SortOrder;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.PaneId;

/** Coordinator instructions returned by action handlers. */
public sealed interface ActionResult {
    record Activate(PaneId pane) implements ActionResult {}
    record Navigate(PaneId pane, @NonNull Path path) implements ActionResult {}
    record History(PaneId pane, boolean forward) implements ActionResult {}
    record Refresh(PaneId pane) implements ActionResult {}
    record RefreshVisible(@Nullable String message) implements ActionResult {}
    record Sort(PaneId pane, @NonNull SortOrder order) implements ActionResult {}
    record ToggleSelection(PaneId pane, @NonNull Item item, boolean enterMode)
            implements ActionResult {}
    record SelectAll(PaneId pane) implements ActionResult {}
    record SelectRange(PaneId pane) implements ActionResult {}
    record ClearSelection(PaneId pane, boolean exitMode) implements ActionResult {}
    record Effect(@NonNull WorkspaceEffect effect) implements ActionResult {}
    record Composite(@NonNull List<ActionResult> changes) implements ActionResult {
        public Composite {
            changes = List.copyOf(changes);
        }
    }
}

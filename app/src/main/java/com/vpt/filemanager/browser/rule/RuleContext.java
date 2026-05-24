package com.vpt.filemanager.browser.rule;

import androidx.annotation.NonNull;

import java.util.List;

import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

public final class RuleContext {
    @NonNull public final Path active;
    @NonNull public final Path inactive;
    @NonNull public final List<Item> selection;

    private RuleContext(Path active, Path inactive, List<Item> selection) {
        this.active = active;
        this.inactive = inactive;
        this.selection = List.copyOf(selection);
    }

    public static RuleContext from(WorkspaceSnapshot state) {
        Path active = state.active().path == null ? StorageBoundary.root() : state.active().path;
        Path inactive = state.inactive().path == null ? StorageBoundary.root() : state.inactive().path;
        return new RuleContext(active, inactive, state.active().selectedItems());
    }
}

package com.vpt.filemanager.browser.ui.pane.controller;

import android.app.AlertDialog;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.List;

import com.vpt.filemanager.R;
import com.vpt.filemanager.browser.action.bookmark.AddBookmarkAction;
import com.vpt.filemanager.browser.action.bookmark.RemoveBookmarksAction;
import com.vpt.filemanager.browser.action.entry.CompressAction;
import com.vpt.filemanager.browser.action.entry.DeleteEntriesAction;
import com.vpt.filemanager.browser.action.entry.RenameEntryAction;
import com.vpt.filemanager.browser.action.open.OpenWithAction;
import com.vpt.filemanager.browser.action.properties.PropertiesAction;
import com.vpt.filemanager.browser.action.open.ToolsAction;
import com.vpt.filemanager.browser.action.selection.ClearSelectionAction;
import com.vpt.filemanager.browser.action.selection.SelectAllAction;
import com.vpt.filemanager.browser.action.selection.SelectRangeAction;
import com.vpt.filemanager.browser.action.share.ShareAction;
import com.vpt.filemanager.browser.action.transfer.TransferMode;
import com.vpt.filemanager.browser.action.trash.RestoreTrashAction;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.ui.dialog.EntryActionsBottomSheet;
import com.vpt.filemanager.browser.ui.dialog.NameInputDialog;
import com.vpt.filemanager.browser.ui.pane.DualPaneHostFragment;
import com.vpt.filemanager.browser.ui.pane.flow.TransferEntriesFlow;
import com.vpt.filemanager.browser.workspace.state.PaneState;

public final class SelectionBarController {
    private static final float DISABLED_ALPHA = 0.38f;
    private final DualPaneHostFragment host;
    private final FragmentDualPaneHostBinding binding;

    public SelectionBarController(DualPaneHostFragment host, FragmentDualPaneHostBinding binding) {
        this.host = host;
        this.binding = binding;
    }

    public void attach() {
        binding.btnSelAll.setOnClickListener(view -> host.dispatch(new SelectAllAction(host.activePaneId())));
        binding.btnSelDeselect.setOnClickListener(view ->
                host.dispatch(new ClearSelectionAction(host.activePaneId(), false)));
        binding.btnSelCancel.setOnClickListener(view ->
                host.dispatch(new ClearSelectionAction(host.activePaneId(), true)));
        binding.btnSelRange.setOnClickListener(view ->
                host.dispatch(new SelectRangeAction(host.activePaneId())));
        binding.btnSelMore.setOnClickListener(view -> contextAction());
    }

    public void render(PaneState state, ToolbarController toolbar) {
        boolean active = state.selectionMode;
        binding.bottomBar.setVisibility(active ? View.GONE : View.VISIBLE);
        binding.selectionBar.setVisibility(active ? View.VISIBLE : View.GONE);
        if (!active) return;
        int count = state.selection.size();
        toolbar.setTitle(host.getString(R.string.selected_count, count));
        toolbar.setSubtitle("");
        applyEnabled(binding.btnSelRange, count >= 2);
        applyEnabled(binding.btnSelDeselect, count > 0);
        applyEnabled(binding.btnSelMore, count > 0);
        if (state.path != null && state.path.isTrash()) {
            binding.btnSelMore.setImageResource(R.drawable.ic_restore);
        } else if (state.path != null && state.path.isBookmarks()) {
            binding.btnSelMore.setImageResource(R.drawable.ic_bookmark_remove);
        } else {
            binding.btnSelMore.setImageResource(R.drawable.ic_more);
        }
    }

    private void contextAction() {
        PaneState state = host.activeState();
        List<Item> selected = state.selectedItems();
        if (selected.isEmpty()) return;
        if (state.path != null && state.path.isTrash()) {
            host.dispatch(new RestoreTrashAction(host.activePaneId(), selected));
        } else if (state.path != null && state.path.isBookmarks()) {
            host.dispatch(new RemoveBookmarksAction(host.activePaneId(), selected));
        } else {
            showMore(selected);
        }
    }

    private void showMore(List<Item> selected) {
        Item single = selected.size() == 1 ? selected.get(0) : null;
        EntryActionsBottomSheet.newInstance(single == null ? selected.size() + " items" : single.name())
                .setDisabledActions(host.disabledActions())
                .setListener(action -> handleAction(action, selected, single))
                .show(host.getChildFragmentManager(), "selection-more");
    }

    private void handleAction(EntryActionsBottomSheet.Action action, List<Item> selected,
                              @Nullable Item single) {
        switch (action) {
            case DELETE -> confirmDelete(selected);
            case SHARE -> host.dispatch(new ShareAction(host.activePaneId(), selected));
            case RENAME -> {
                if (single != null) NameInputDialog.show(host.requireContext(), R.string.action_rename,
                        R.string.file_name, single.name(), name -> host.dispatch(
                                new RenameEntryAction(host.activePaneId(), single, name)));
            }
            case PROPERTIES -> {
                if (single != null) host.dispatch(new PropertiesAction(host.activePaneId(), single));
            }
            case OPEN_WITH -> {
                if (single != null) host.dispatch(new OpenWithAction(host.activePaneId(), single));
            }
            case BOOKMARK -> {
                if (single != null) host.dispatch(new AddBookmarkAction(host.activePaneId(), single));
            }
            case COPY -> new TransferEntriesFlow(host).execute(TransferMode.COPY);
            case MOVE -> new TransferEntriesFlow(host).execute(TransferMode.MOVE);
            case COMPRESS -> host.dispatch(new CompressAction(host.activePaneId(), selected));
            case TOOLS -> host.dispatch(new ToolsAction(host.activePaneId(), selected));
        }
    }

    private void confirmDelete(List<Item> selected) {
        new AlertDialog.Builder(host.requireContext()).setTitle(R.string.action_delete)
                .setMessage(selected.size() == 1 ? selected.get(0).name()
                        : host.getString(R.string.delete_confirm_count, selected.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        host.dispatch(new DeleteEntriesAction(host.activePaneId(), selected)))
                .show();
    }

    private static void applyEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : DISABLED_ALPHA);
    }
}

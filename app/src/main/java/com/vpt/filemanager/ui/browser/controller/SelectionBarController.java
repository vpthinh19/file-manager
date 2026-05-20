package com.vpt.filemanager.ui.browser.controller;

import java.util.EnumSet;
import java.util.Set;

import android.app.AlertDialog;
import android.view.View;

import androidx.annotation.Nullable;

import com.vpt.filemanager.R;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.ui.browser.DualPaneHostFragment;
import com.vpt.filemanager.ui.browser.NodeActionsBottomSheet;
import com.vpt.filemanager.ui.browser.PaneViewModel;
import com.vpt.filemanager.ui.browser.action.ShareAction;
import com.vpt.filemanager.ui.browser.dialog.NameInputDialog;
import com.vpt.filemanager.ui.properties.PropertiesDialogFragment;

/**
 * Quản lý selection mode UI: 4-button selection bar (cancel / select-all / deselect / more),
 * "More" bottom sheet với 10 actions, disable rules per selection state, action handlers.
 *
 * <p>Disable rules (xem {@link #computeDisabledActions}):
 * <ul>
 *   <li>multi-select → disable RENAME/PROPERTIES/OPEN_WITH/BOOKMARK (single-target only)</li>
 *   <li>single folder → disable OPEN_WITH (no external viewer for folders)</li>
 *   <li>single file → disable BOOKMARK (v1: bookmarks are folders)</li>
 *   <li>any archive entry → disable RENAME/DELETE/MOVE/COMPRESS (read-only)</li>
 * </ul>
 */
public final class SelectionBarController {
    private final DualPaneHostFragment host;
    private final FragmentDualPaneHostBinding binding;
    private final ShareAction shareAction;

    public SelectionBarController(DualPaneHostFragment host,
                                   FragmentDualPaneHostBinding binding,
                                   ShareAction shareAction) {
        this.host = host;
        this.binding = binding;
        this.shareAction = shareAction;
    }

    public void attach() {
        binding.btnSelCancel.setOnClickListener(v -> host.activeVm().clearSelection());
        binding.btnSelAll.setOnClickListener(v -> host.activeVm().selectAllVisible());
        // Deselect-all = same end-state as Cancel (X), nhưng icon truyền tải "drop selection"
        // rõ hơn — power users tìm explicit affordance thay vì đoán X = "exit" hay "discard".
        binding.btnSelDeselect.setOnClickListener(v -> host.activeVm().clearSelection());
        binding.btnSelMore.setOnClickListener(v -> showMoreSheet());
    }

    /**
     * Toggle bottom bar visibility theo selection state. Khi vào selection mode, toolbar title
     * cũng đổi sang "N selected" — caller (DualPaneHostFragment) gọi {@link ToolbarController#setTitle}
     * sau renderBars để hiển thị count.
     */
    public void renderBars(@Nullable Set<FilePath> selection, ToolbarController toolbarCtrl) {
        boolean inMode = selection != null && !selection.isEmpty();
        binding.bottomBar.setVisibility(inMode ? View.GONE : View.VISIBLE);
        binding.selectionBar.setVisibility(inMode ? View.VISIBLE : View.GONE);
        if (inMode) {
            toolbarCtrl.setTitle(host.getString(R.string.selected_count, selection.size()));
            toolbarCtrl.setSubtitle("");
        }
    }

    private void showMoreSheet() {
        PaneViewModel vm = host.activeVm();
        Set<FilePath> selection = vm.selection().getValue();
        if (selection == null || selection.isEmpty()) {
            return;
        }
        boolean single = selection.size() == 1;
        FilePath singlePath = single ? selection.iterator().next() : null;
        VirtualNode singleNode = single ? vm.findNode(singlePath) : null;

        NodeActionsBottomSheet sheet = NodeActionsBottomSheet
                .newInstance(single ? singlePath.name() : selection.size() + " items")
                .setDisabledActions(computeDisabledActions(selection, singleNode))
                .setListener(action -> handleAction(action, singlePath));
        sheet.show(host.getChildFragmentManager(), "selection-more");
    }

    private EnumSet<NodeActionsBottomSheet.Action> computeDisabledActions(
            Set<FilePath> selection, @Nullable VirtualNode singleNode) {
        EnumSet<NodeActionsBottomSheet.Action> disabled =
                EnumSet.noneOf(NodeActionsBottomSheet.Action.class);
        boolean multi = selection.size() > 1;
        if (multi) {
            disabled.add(NodeActionsBottomSheet.Action.RENAME);
            disabled.add(NodeActionsBottomSheet.Action.PROPERTIES);
            disabled.add(NodeActionsBottomSheet.Action.OPEN_WITH);
            disabled.add(NodeActionsBottomSheet.Action.BOOKMARK);
        } else if (singleNode != null) {
            if (singleNode.isFolder()) {
                disabled.add(NodeActionsBottomSheet.Action.OPEN_WITH);
            } else {
                disabled.add(NodeActionsBottomSheet.Action.BOOKMARK);
            }
        }
        for (FilePath p : selection) {
            if (p.isArchive()) {
                disabled.add(NodeActionsBottomSheet.Action.RENAME);
                disabled.add(NodeActionsBottomSheet.Action.DELETE);
                disabled.add(NodeActionsBottomSheet.Action.MOVE);
                disabled.add(NodeActionsBottomSheet.Action.COMPRESS);
                break;
            }
        }
        return disabled;
    }

    private void handleAction(NodeActionsBottomSheet.Action action, @Nullable FilePath singlePath) {
        PaneViewModel vm = host.activeVm();
        switch (action) {
            case DELETE:
                confirmDeleteSelected();
                break;
            case SHARE:
                shareAction.execute();
                break;
            case RENAME:
                if (singlePath == null) {
                    host.toast(host.getString(R.string.selection_single_only));
                } else {
                    final FilePath target = singlePath;
                    NameInputDialog.show(host.requireContext(),
                            R.string.action_rename, R.string.file_name,
                            newName -> vm.rename(target, newName));
                }
                break;
            case PROPERTIES:
                if (singlePath == null) {
                    host.toast(host.getString(R.string.selection_single_only));
                } else {
                    PropertiesDialogFragment.newInstance(singlePath.toString())
                            .show(host.getChildFragmentManager(), "properties");
                }
                break;
            case OPEN_WITH:
                if (singlePath == null) {
                    host.toast(host.getString(R.string.selection_single_only));
                } else if (singlePath.isLocal()) {
                    host.openWithPath(singlePath);
                }
                break;
            case COPY:
            case MOVE:
            case TOOLS:
            case COMPRESS:
            case BOOKMARK:
            default:
                host.toast(host.getString(action.labelRes) + " — coming in Phase 2C");
                break;
        }
    }

    private void confirmDeleteSelected() {
        PaneViewModel vm = host.activeVm();
        Set<FilePath> selection = vm.selection().getValue();
        if (selection == null || selection.isEmpty()) {
            return;
        }
        int count = selection.size();
        new AlertDialog.Builder(host.requireContext())
                .setTitle(R.string.action_delete)
                .setMessage(count == 1
                        ? selection.iterator().next().name()
                        : host.getString(R.string.delete_confirm_count, count))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> vm.deleteSelected())
                .show();
    }
}

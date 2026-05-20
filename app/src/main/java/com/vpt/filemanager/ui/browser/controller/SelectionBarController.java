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
 * Quản lý selection mode UI: 5-button selection bar (select_all / deselect / X / range / more),
 * "More" bottom sheet với 10 actions, disable rules per selection state, action handlers.
 *
 * <p>Phase R-7b: 5-th button context-aware theo scheme của active pane:
 * <ul>
 *   <li>Default (file/archive): icon {@code ic_more}, click mở More sheet</li>
 *   <li>Trash (scheme=trash): icon {@code ic_restore}, click restore selected entries trực tiếp
 *       — không qua sheet (1 action duy nhất hợp lý ở Trash; DELETE_FOREVER per-item defer v2,
 *       Empty Trash ở overflow toolbar)</li>
 *   <li>Bookmark (scheme=bookmark): icon {@code ic_bookmark_remove}, click remove selected
 *       bookmarks trực tiếp</li>
 * </ul>
 *
 * <p>Disable rules cho More sheet (xem {@link #computeDisabledActions}):
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

    private static final float DISABLED_ALPHA = 0.38f;

    public void attach() {
        // Phase R-7a: 5-button layout với mode-aware semantics.
        // X = exit mode hẳn (clear items + tắt flag). Deselect = clear items NHƯNG giữ mode.
        binding.btnSelAll.setOnClickListener(v -> host.activeVm().selectAllVisible());
        binding.btnSelDeselect.setOnClickListener(v -> host.activeVm().clearSelection());
        binding.btnSelCancel.setOnClickListener(v -> host.activeVm().exitSelectionMode());
        binding.btnSelRange.setOnClickListener(v -> host.activeVm().selectRange());
        // R-7b: dispatch theo pane scheme lúc click thay vì re-set listener mỗi render →
        // single click handler, branch ngắn, icon update riêng trong renderBars.
        binding.btnSelMore.setOnClickListener(v -> onMoreOrContextAction());
    }

    private void onMoreOrContextAction() {
        PaneViewModel vm = host.activeVm();
        FilePath current = vm.currentPath();
        if (current != null && current.isTrash()) {
            vm.restoreSelected();
            return;
        }
        if (current != null && current.isBookmark()) {
            vm.removeBookmarksSelected();
            return;
        }
        showMoreSheet();
    }

    /**
     * Render selection bar visibility + button enabled state theo (mode, selection count).
     *
     * <p>Phase R-7a: signature thêm {@code inMode} param — visibility giờ drive by flag (không
     * derived từ "selection non-empty"). Cho phép "0 selected" state hiển thị selection bar khi
     * user deselect-all nhưng chưa exit mode.
     *
     * <p>Range button enabled chỉ khi {@code selection.size() >= 2} — defensive (logic cũng có
     * trong VM.selectRange()).
     */
    public void renderBars(@Nullable Boolean inMode, @Nullable Set<FilePath> selection,
                            ToolbarController toolbarCtrl) {
        boolean modeActive = Boolean.TRUE.equals(inMode);
        binding.bottomBar.setVisibility(modeActive ? View.GONE : View.VISIBLE);
        binding.selectionBar.setVisibility(modeActive ? View.VISIBLE : View.GONE);
        int count = selection == null ? 0 : selection.size();
        if (modeActive) {
            toolbarCtrl.setTitle(host.getString(R.string.selected_count, count));
            toolbarCtrl.setSubtitle("");
            boolean canRange = count >= 2;
            binding.btnSelRange.setEnabled(canRange);
            binding.btnSelRange.setAlpha(canRange ? 1f : DISABLED_ALPHA);
            // Deselect chỉ làm gì khi có items — disable khi count==0 cho feedback rõ.
            boolean canDeselect = count > 0;
            binding.btnSelDeselect.setEnabled(canDeselect);
            binding.btnSelDeselect.setAlpha(canDeselect ? 1f : DISABLED_ALPHA);
            applyContextualMoreIcon();
            // "More" button enabled chỉ khi có selection (mọi context — restore/remove/sheet đều
            // cần items). Mirror Deselect alpha rule.
            binding.btnSelMore.setEnabled(canDeselect);
            binding.btnSelMore.setAlpha(canDeselect ? 1f : DISABLED_ALPHA);
        }
    }

    /**
     * Swap btn_sel_more icon theo scheme active pane. Gọi trong {@link #renderBars} mỗi lần
     * mode/selection thay đổi — render là idempotent setImageResource nên không tốn.
     */
    private void applyContextualMoreIcon() {
        FilePath current = host.activeVm().currentPath();
        int iconRes;
        if (current != null && current.isTrash()) {
            iconRes = R.drawable.ic_restore;
        } else if (current != null && current.isBookmark()) {
            iconRes = R.drawable.ic_bookmark_remove;
        } else {
            iconRes = R.drawable.ic_more;
        }
        binding.btnSelMore.setImageResource(iconRes);
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

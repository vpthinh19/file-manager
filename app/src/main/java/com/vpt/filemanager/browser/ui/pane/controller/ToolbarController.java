package com.vpt.filemanager.browser.ui.pane.controller;

import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.vpt.filemanager.R;
import com.vpt.filemanager.browser.action.browse.ChangeSortAction;
import com.vpt.filemanager.browser.action.browse.RefreshAction;
import com.vpt.filemanager.browser.action.browse.SearchAction;
import com.vpt.filemanager.browser.action.trash.EmptyTrashAction;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.ui.dialog.SearchDialog;
import com.vpt.filemanager.browser.ui.dialog.SortBottomSheet;
import com.vpt.filemanager.browser.ui.drawer.DrawerHost;
import com.vpt.filemanager.browser.ui.format.ByteSize;
import com.vpt.filemanager.browser.ui.pane.DualPaneHostFragment;
import com.vpt.filemanager.browser.workspace.state.PaneState;
import java.io.File;

public final class ToolbarController {
    private final DualPaneHostFragment host;
    private final FragmentDualPaneHostBinding binding;

    public ToolbarController(DualPaneHostFragment host, FragmentDualPaneHostBinding binding) {
        this.host = host;
        this.binding = binding;
    }

    public void attach() {
        binding.toolbar.setNavigationOnClickListener(view -> {
            if (host.requireActivity() instanceof DrawerHost drawer) drawer.openDrawer();
        });
        binding.toolbar.inflateMenu(R.menu.menu_dual_pane_overflow);
        Drawable overflow = AppCompatResources.getDrawable(host.requireContext(), R.drawable.ic_more);
        if (overflow != null) {
            overflow = DrawableCompat.wrap(overflow.mutate());
            DrawableCompat.setTint(overflow,
                    ContextCompat.getColor(host.requireContext(), R.color.md_chrome_on_bg));
            binding.toolbar.setOverflowIcon(overflow);
        }
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);
    }

    private boolean onMenuItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            host.dispatch(new RefreshAction(host.activePaneId()));
        } else if (id == R.id.action_sort) {
            SortBottomSheet.newInstance(host.activeState().sortOrder)
                    .setListener(order -> host.dispatch(new ChangeSortAction(host.activePaneId(), order)))
                    .show(host.getChildFragmentManager(), "sort");
        } else if (id == R.id.action_empty_trash) {
            new AlertDialog.Builder(host.requireContext())
                    .setTitle(R.string.trash_empty_title).setMessage(R.string.trash_empty_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> host.dispatch(new EmptyTrashAction(host.activePaneId())))
                    .show();
        } else if (id == R.id.action_search) {
            SearchDialog.show(host.requireContext(),
                    query -> host.dispatch(new SearchAction(host.activePaneId(), query)));
        } else if (id == R.id.action_settings) {
            host.toast(host.getString(R.string.coming_soon));
        } else if (id == R.id.action_exit) {
            host.requireActivity().finishAffinity();
        } else {
            return false;
        }
        return true;
    }

    public void renderState(PaneState state) {
        Path path = state.path;
        MenuItem emptyTrash = binding.toolbar.getMenu().findItem(R.id.action_empty_trash);
        if (emptyTrash != null) emptyTrash.setVisible(path != null && path.isTrash());
        if (path == null) {
            setTitle("");
            setSubtitle("");
            return;
        }
        setTitle(display(path));
        if (state.error != null) {
            setSubtitle(state.error);
        } else if (path.isSearch()) {
            setSubtitle(host.getString(R.string.stats_search_results, state.folderCount + state.fileCount));
        } else if (state.totalBytes > 0) {
            setSubtitle(host.getString(R.string.stats_with_disk, state.folderCount, state.fileCount,
                    ByteSize.format(state.freeBytes), ByteSize.format(state.totalBytes)));
        } else {
            setSubtitle(host.getString(R.string.stats_basic, state.folderCount, state.fileCount));
        }
    }

    public void setTitle(CharSequence text) { binding.tvToolbarTitle.setText(text); }
    public void setSubtitle(CharSequence text) { binding.tvToolbarSubtitle.setText(text == null ? "" : text); }

    private String display(Path path) {
        if (path.isTrash()) return host.getString(R.string.action_trash);
        if (path.isBookmarks()) return host.getString(R.string.menu_bookmarks);
        if (path.isSearch()) return host.getString(R.string.search_title, path.query());
        if (path.isArchive()) return new File(path.container()).getName() + path.directory();
        return path.directory();
    }
}

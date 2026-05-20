package com.vpt.filemanager.ui.browser.controller;

import android.app.AlertDialog;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.ByteSize;
import com.vpt.filemanager.core.StorageScope;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.ui.DrawerHost;
import com.vpt.filemanager.ui.browser.DualPaneHostFragment;
import com.vpt.filemanager.ui.browser.PaneViewModel;
import com.vpt.filemanager.ui.browser.SortBottomSheet;

/**
 * Manage toolbar: navigation icon (mở drawer), overflow menu router, title/subtitle render theo
 * pane UiState. Extract từ DualPaneHostFragment ở Phase R-5a.
 *
 * <p>Menu router KISS — 5 id direct switch, không Command pattern. Items "coming soon" toast khớp
 * behavior hiện tại để user không bị surprise UI.
 */
public final class ToolbarController {
    private final DualPaneHostFragment host;
    private final FragmentDualPaneHostBinding binding;

    public ToolbarController(DualPaneHostFragment host, FragmentDualPaneHostBinding binding) {
        this.host = host;
        this.binding = binding;
    }

    public void attach() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (host.requireActivity() instanceof DrawerHost dh) {
                dh.openDrawer();
            }
        });
        binding.toolbar.inflateMenu(R.menu.menu_dual_pane_overflow);
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItem);
    }

    private boolean onMenuItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            host.activeVm().refresh();
            return true;
        }
        if (id == R.id.action_sort) {
            SortBottomSheet.newInstance(host.activeVm().sortOrder())
                    .setListener(host.activeVm()::setSort)
                    .show(host.getChildFragmentManager(), "sort");
            return true;
        }
        if (id == R.id.action_search || id == R.id.action_settings) {
            host.toast(host.getString(R.string.coming_soon));
            return true;
        }
        if (id == R.id.action_exit) {
            new AlertDialog.Builder(host.requireContext())
                    .setTitle(R.string.exit_title)
                    .setMessage(R.string.exit_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok,
                            (d, w) -> host.requireActivity().finishAffinity())
                    .show();
            return true;
        }
        return false;
    }

    public void renderState(@Nullable PaneViewModel.UiState state) {
        if (state instanceof PaneViewModel.UiState.Content content) {
            setTitle(displayPath(content.path));
            if (content.path.isArchive()) {
                setSubtitle(host.getString(R.string.stats_archive,
                        content.folderCount + content.fileCount));
            } else if (content.totalBytes > 0) {
                setSubtitle(host.getString(R.string.stats_with_disk,
                        content.folderCount, content.fileCount,
                        ByteSize.format(content.freeBytes), ByteSize.format(content.totalBytes)));
            } else {
                setSubtitle(host.getString(R.string.stats_basic,
                        content.folderCount, content.fileCount));
            }
        } else if (state instanceof PaneViewModel.UiState.Roots roots) {
            setTitle(StorageScope.ROOT_PATH);
            setSubtitle(host.getString(R.string.stats_roots, roots.roots.size()));
        } else if (state instanceof PaneViewModel.UiState.Empty empty) {
            setTitle(displayPath(empty.path));
            setSubtitle(host.getString(R.string.stats_basic, 0, 0));
        } else if (state instanceof PaneViewModel.UiState.Error error) {
            setTitle(displayPath(error.path));
            setSubtitle(error.message == null || error.message.isEmpty()
                    ? host.getString(R.string.error_listing_denied)
                    : error.message);
        } else {
            setTitle("");
            setSubtitle("");
        }
    }

    public void setTitle(@NonNull CharSequence text) {
        binding.tvToolbarTitle.setText(text);
    }

    public void setSubtitle(@Nullable CharSequence text) {
        binding.tvToolbarSubtitle.setText(text == null ? "" : text);
    }

    /** archive://...!/inner path UX-friendly: show {@code archiveFile + "!" + innerPath}. */
    @NonNull
    private static String displayPath(@NonNull FilePath path) {
        if (path.isArchive()) {
            FilePath archiveFile = FilePath.parse(path.authority());
            return archiveFile.path() + "!" + path.path();
        }
        return path.path();
    }
}

package com.vpt.filemanager.ui.browser.action;

import java.io.File;

import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.ui.NameDeconflict;
import com.vpt.filemanager.ui.browser.DualPaneHostFragment;
import com.vpt.filemanager.ui.browser.PaneViewModel;
import com.vpt.filemanager.ui.browser.dialog.ConflictDialog;
import com.vpt.filemanager.ui.browser.dialog.CreateItemDialog;

/**
 * User click "+" trên bottom bar → tạo file/folder mới, có resolve conflict (Replace / Keep both /
 * Cancel). Extract từ DualPaneHostFragment.showCreateDialog + attemptCreate +
 * showCreateConflictDialog ở Phase R-5a.
 *
 * <p>Instance-per-Fragment (mỗi DualPaneHostFragment có 1 CreateAction, lifecycle aligned). Hold
 * host reference để access activeVm() + requireContext(). Set null trong host.onDestroyView().
 *
 * <p>Phase R-5b: thay {@code File.exists()} pre-check bằng {@code parent.children().contains(name)}
 * qua VirtualNode (cross-source aware). Hiện tại giữ logic local-only để smoke parity.
 */
public final class CreateAction {
    private final DualPaneHostFragment host;

    public CreateAction(DualPaneHostFragment host) {
        this.host = host;
    }

    public void execute() {
        CreateItemDialog.show(host.requireContext(), this::attemptCreate);
    }

    private void attemptCreate(boolean isFolder, String name) {
        PaneViewModel vm = host.activeVm();
        FilePath current = vm.currentPath();
        if (current == null || !current.isLocal()) {
            return;
        }
        File target = new File(current.path(), name);
        if (!target.exists()) {
            if (isFolder) {
                vm.createFolder(name);
            } else {
                vm.createFile(name);
            }
            return;
        }
        showConflict(isFolder, name, current);
    }

    private void showConflict(boolean isFolder, String name, FilePath dir) {
        File dirFile = new File(dir.path());
        ConflictDialog.show(host.requireContext(), name, new ConflictDialog.OnChoice() {
            @Override
            public void onReplace() {
                host.activeVm().deleteThenCreate(dir.child(name), isFolder);
            }

            @Override
            public void onKeepBoth() {
                String unique = NameDeconflict.unique(dirFile, name);
                PaneViewModel vm = host.activeVm();
                if (isFolder) {
                    vm.createFolder(unique);
                } else {
                    vm.createFile(unique);
                }
            }
        });
    }
}

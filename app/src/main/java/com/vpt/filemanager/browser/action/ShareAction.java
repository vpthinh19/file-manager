package com.vpt.filemanager.browser.action;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.vpt.filemanager.R;
import com.vpt.filemanager.node.FilePath;
import com.vpt.filemanager.browser.DualPaneHostFragment;
import com.vpt.filemanager.browser.PaneViewModel;

/**
 * Share selected files qua {@link Intent#ACTION_SEND} (1 item) hoặc {@link Intent#ACTION_SEND_MULTIPLE}.
 * Build URI qua {@link FileProvider} với authority {@code package + .fileprovider}.
 *
 * <p>Archive entries skip — FileProvider yêu cầu File thật. Nếu tất cả selection là archive entry
 * → uris rỗng → toast "unavailable" + không launch chooser.
 */
public final class ShareAction {
    private final DualPaneHostFragment host;

    public ShareAction(DualPaneHostFragment host) {
        this.host = host;
    }

    public void execute() {
        PaneViewModel vm = host.activeVm();
        Set<FilePath> selection = vm.selection().getValue();
        if (selection == null || selection.isEmpty()) {
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        String authority = host.requireContext().getPackageName() + ".fileprovider";
        for (FilePath p : selection) {
            if (!p.isLocal()) {
                continue;
            }
            try {
                uris.add(FileProvider.getUriForFile(
                        host.requireContext(), authority, new File(p.path())));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (uris.isEmpty()) {
            host.toast(host.getString(R.string.unavailable));
            return;
        }
        Intent intent;
        if (uris.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("*/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.startActivity(Intent.createChooser(intent, host.getString(R.string.action_share)));
            vm.clearSelection();
        } catch (ActivityNotFoundException e) {
            host.toast(e.getMessage() == null
                    ? host.getString(R.string.unavailable) : e.getMessage());
        }
    }
}

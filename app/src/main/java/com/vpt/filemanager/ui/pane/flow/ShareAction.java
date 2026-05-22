package com.vpt.filemanager.ui.pane.flow;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.vpt.filemanager.R;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.ui.pane.DualPaneHostFragment;
import com.vpt.filemanager.ui.pane.PaneViewModel;
import com.vpt.filemanager.operations.share.PrepareShareRequestOperation;
import com.vpt.filemanager.operations.share.ShareRequest;

/**
 * Share selected files qua {@link Intent#ACTION_SEND} (1 item) hoặc {@link Intent#ACTION_SEND_MULTIPLE}.
 * Build URI qua {@link FileProvider} với authority {@code package + .fileprovider}.
 *
 * <p>Archive entries skip — FileProvider yêu cầu File thật. Nếu tất cả selection là archive entry
 * → uris rỗng → toast "unavailable" + không launch chooser.
 */
public final class ShareAction {
    private final DualPaneHostFragment host;
    private final PrepareShareRequestOperation prepareShareRequestOperation =
            new PrepareShareRequestOperation();

    public ShareAction(DualPaneHostFragment host) {
        this.host = host;
    }

    public void execute() {
        PaneViewModel vm = host.activeVm();
        Set<NodePath> selection = vm.selection().getValue();
        if (selection == null || selection.isEmpty()) {
            return;
        }
        List<VirtualNode> nodes = new ArrayList<>(selection.size());
        for (NodePath path : selection) {
            VirtualNode node = vm.findNode(path);
            if (node != null) {
                nodes.add(node);
            }
        }
        ShareRequest request = prepareShareRequestOperation.execute(
                new PrepareShareRequestOperation.Input(nodes));
        if (request.isEmpty()) {
            host.toast(host.getString(R.string.unavailable));
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        String authority = host.requireContext().getPackageName() + ".fileprovider";
        for (NodePath p : request.localPaths) {
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
        if (!request.multiple()) {
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

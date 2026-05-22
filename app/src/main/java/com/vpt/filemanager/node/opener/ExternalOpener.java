package com.vpt.filemanager.node.opener;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.vpt.filemanager.R;
import com.vpt.filemanager.format.MimeTypes;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Fallback opener: launch system "Open with" chooser qua {@link Intent#ACTION_VIEW}.
 *
 * <p>Phải đặt CUỐI cùng trong {@link OpenerRegistry} priority list — TextOpener / ArchiveOpener /
 * (Image|Video|AudioOpener Phase 2D) phải có cơ hội match trước. ExternalOpener chỉ match khi
 * không có opener "in-app" handle được loại file đó (vd .pdf khi chưa có PdfOpener, .docx, ...).
 *
 * <p><b>Local only</b>: {@link FileProvider#getUriForFile} yêu cầu {@link File} thật, archive
 * entry là stream — không support. Click archive entry không có in-app opener sẽ không có opener
 * match → caller (PaneViewModel ở R-5) detect null + show "OpenAs" dialog hoặc toast.
 */
@Singleton
public final class ExternalOpener implements NodeOpener {

    @Inject
    public ExternalOpener() {
    }

    @Override
    public boolean canOpen(VirtualNode node) {
        return !node.isFolder() && node.path().isLocal();
    }

    @Override
    public void onOpen(VirtualNode node, OpenContext ctx) throws NodeException {
        NodePath path = node.path();
        try {
            String authority = ctx.context().getPackageName() + ".fileprovider";
            Uri uri = FileProvider.getUriForFile(ctx.context(), authority, new File(path.path()));
            String mime = MimeTypes.detect(node.name());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, ctx.context().getString(R.string.action_open_with));
            // ACTION_VIEW launched từ non-Activity context (vd Application) cần NEW_TASK flag.
            // Khi launched từ Activity context (BrowserFragment.requireContext()) thì không cần,
            // nhưng add flag không hại — Android xử lý đúng cả 2 trường hợp.
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.context().startActivity(chooser);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            throw new NodeException("No app can open: " + node.name(), e);
        }
    }
}

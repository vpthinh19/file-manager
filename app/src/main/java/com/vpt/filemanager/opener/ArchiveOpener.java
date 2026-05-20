package com.vpt.filemanager.opener;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.domain.model.FileCategory;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Click file archive (.zip/.7z/.tar/...) → re-wrap node sang VirtualNode với
 * {@link com.vpt.filemanager.node.source.ArchiveSource} + navigate pane vào root của archive.
 *
 * <p>Đây là minh họa rõ nhất cho concept 2-trục orthogonal: source thay đổi (Local → Archive)
 * trong khi UI/pane vẫn navigate cây node như một folder bình thường — không có "chế độ archive"
 * riêng, không có ArchiveFragment, không có view khác.
 *
 * <p><b>Nested archive</b>: v1 chỉ support archive bên trong LOCAL filesystem (không support
 * archive-trong-archive). canOpen() check {@code path.isLocal()}. Phase 2D có thể mở rộng nếu
 * người dùng yêu cầu.
 *
 * <p><b>Format support</b>: hiện ArchiveSource chỉ wrap {@link java.util.zip.ZipFile} — handle
 * được .zip + .jar/.war/.apk (cùng format). TAR/7z/RAR throw NodeException ở
 * {@code ArchiveSource.openOrCache} với message rõ. Phase 3 thêm libarchive native.
 */
@Singleton
public final class ArchiveOpener implements FileOpener {
    private final NodeFactory nodeFactory;

    @Inject
    public ArchiveOpener(NodeFactory nodeFactory) {
        this.nodeFactory = nodeFactory;
    }

    @Override
    public boolean canOpen(VirtualNode node) {
        if (node.isFolder()) {
            return false;
        }
        if (!node.path().isLocal()) {
            return false;
        }
        return FileCategory.ofExtension(node.name()) == FileCategory.ARCHIVE;
    }

    @Override
    public void onOpen(VirtualNode node, OpenContext ctx) throws NodeException {
        VirtualNode archiveRoot = nodeFactory.asArchiveRoot(node);
        // PaneNavigator takes FilePath — caller PaneViewModel re-resolves qua NodeFactory.
        // Identity stays consistent; archiveRoot instance discarded after this call.
        ctx.pane().navigateTo(archiveRoot.path());
    }
}

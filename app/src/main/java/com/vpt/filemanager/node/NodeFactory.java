package com.vpt.filemanager.node;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.node.source.ArchiveSource;
import com.vpt.filemanager.node.source.LocalSource;
import com.vpt.filemanager.node.source.NodeSource;

/**
 * Điểm vào duy nhất để khởi tạo {@link VirtualNode} từ một {@link FilePath}. Dispatch sang đúng
 * {@link NodeSource} theo scheme của path.
 *
 * <p>Sử dụng chủ yếu khi navigation bắt đầu từ một path string (drawer click → root, restore từ
 * SavedStateHandle, back/forward stack pop). Khi navigation đi qua parent listing thì child node
 * được build trực tiếp trong {@code source.list()} — không qua factory.
 *
 * <p>Phase R-4 sẽ thêm {@code TrashSource} và {@code BookmarkSource} vào danh sách dispatch. Hiện
 * tại các scheme đó throw {@link NodeException} (chưa wired UI nên không reach).
 *
 * <p>Phase R-3 sẽ thêm helper {@code asArchiveRoot(VirtualNode zipFileNode)} cho ArchiveOpener:
 * khi user click file .zip, opener cần re-wrap node thành VirtualNode với {@code source =
 * ArchiveSource} thay vì LocalSource — đó là logic "mở archive như folder".
 */
@Singleton
public final class NodeFactory {
    private final LocalSource localSource;
    private final ArchiveSource archiveSource;

    @Inject
    public NodeFactory(LocalSource localSource, ArchiveSource archiveSource) {
        this.localSource = localSource;
        this.archiveSource = archiveSource;
    }

    /**
     * Stat path + build {@link VirtualNode} với source phù hợp.
     *
     * @throws NodeException khi path không tồn tại, scheme không support, hoặc stat fail
     */
    public VirtualNode fromPath(FilePath path) throws NodeException {
        if (path.isLocal()) {
            return localSource.resolve(path);
        }
        if (path.isArchive()) {
            return archiveSource.resolve(path);
        }
        // Phase R-4: SCHEME_TRASH + SCHEME_BOOKMARK sẽ wire ở đây
        throw new NodeException("Unsupported scheme: " + path.scheme());
    }

    /**
     * Wrap một file local (vd {@code photos.zip}) thành virtual folder của archive root. Sử dụng
     * bởi {@code ArchiveOpener} khi user click file archive — chuyển source từ LocalSource sang
     * ArchiveSource để PaneViewModel có thể navigate "vào trong" zip như folder bình thường.
     *
     * @throws NodeException khi node không phải file local hoặc archive không mở được
     */
    public VirtualNode asArchiveRoot(VirtualNode localArchiveFile) throws NodeException {
        FilePath localPath = localArchiveFile.path();
        if (!localPath.isLocal()) {
            throw new NodeException("Only local files can be opened as archive: " + localPath);
        }
        if (localArchiveFile.isFolder()) {
            throw new NodeException("Cannot open folder as archive: " + localPath);
        }
        FilePath archiveRoot = FilePath.inArchive(localPath, "/");
        return archiveSource.resolve(archiveRoot);
    }
}

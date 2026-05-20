package com.vpt.filemanager.operations;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Facade cho file CRUD trên VirtualNode tree. Inspect node, dispatch xuống
 * {@link com.vpt.filemanager.node.source.NodeSource} write API.
 *
 * <p>Phase R-4 ship 4 op cơ bản: createFile / createFolder / rename / delete.
 *
 * <p>Phase 2C-6 (Copy/Move + bulk + conflict) sẽ thêm:
 * <ul>
 *   <li>{@code copy(VirtualNode src, VirtualNode targetFolder, String newName, ConflictPolicy)}</li>
 *   <li>{@code move(...)} — atomic khi cùng filesystem, copy+delete otherwise</li>
 *   <li>{@code copyBatch(List<VirtualNode>, VirtualNode, ConflictPolicy, ProgressReporter)}</li>
 * </ul>
 *
 * <p>Trash soft-delete KHÔNG đi qua FileOps — dùng {@link TrashOps#moveToTrash(VirtualNode)}.
 * FileOps.delete() là PERMANENT.
 *
 * <p>Stateless singleton — không hold reference VM/Fragment. Caller (PaneViewModel) wrap call
 * trong {@code executors.io().submit(...)} + catch NodeException ở boundary để format Toast/Dialog.
 */
@Singleton
public final class FileOps {

    @Inject
    public FileOps() {
    }

    public VirtualNode createFile(VirtualNode parentFolder, String name) throws NodeException {
        validateWritableFolder(parentFolder);
        FilePath childPath = parentFolder.path().child(name);
        return parentFolder.source().createFile(childPath);
    }

    public VirtualNode createFolder(VirtualNode parentFolder, String name) throws NodeException {
        validateWritableFolder(parentFolder);
        FilePath childPath = parentFolder.path().child(name);
        return parentFolder.source().createFolder(childPath);
    }

    /**
     * Đổi tên trong cùng folder. Tên mới validate cơ bản (không rỗng, không chứa '/').
     * FilePath.child() đã throw nếu name chứa separator.
     */
    public VirtualNode rename(VirtualNode node, String newName) throws NodeException {
        if (newName == null || newName.isBlank()) {
            throw new NodeException("New name is empty");
        }
        validateWritable(node);
        return node.source().rename(node, newName.trim());
    }

    /**
     * <b>Permanent delete</b>. Folder = xóa đệ quy. Gọi {@link TrashOps#moveToTrash} cho soft delete.
     */
    public void delete(VirtualNode node) throws NodeException {
        validateWritable(node);
        node.source().delete(node);
    }

    private static void validateWritable(VirtualNode node) throws NodeException {
        if (!node.source().supportsWrite()) {
            throw new NodeException("Source is read-only: " + node.path().scheme());
        }
    }

    private static void validateWritableFolder(VirtualNode parent) throws NodeException {
        if (!parent.isFolder()) {
            throw new NodeException("Parent must be a folder: " + parent.path());
        }
        validateWritable(parent);
    }
}

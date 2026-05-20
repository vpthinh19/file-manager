package com.vpt.filemanager.operations;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.core.StorageScope;
import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.data.db.entity.TrashEntryEntity;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Orchestrator cho Trash bin — phối hợp file system (move file vào {@code .AppTrash/}) và
 * Room ({@link TrashDao} ghi metadata để Restore biết original path).
 *
 * <p>v1 chỉ trash node thuộc {@link com.vpt.filemanager.node.source.LocalSource}. Archive entries
 * không trash được (read-only source).
 *
 * <p>Layout đĩa: {@code /storage/emulated/0/.AppTrash/files/{uuid}/{originalName}}.
 * UUID per-entry tránh collision khi user xóa 2 file cùng tên ở 2 folder khác nhau.
 *
 * <p><b>Atomic guarantee</b>: move file xong mới insert Room. Nếu insert fail → file đã ở trash
 * → user thấy file biến mất nhưng list Trash không có → bug. v1 chấp nhận: Room insert hiếm khi
 * fail (table đơn giản), nếu fail thì throw + caller toast. Phase 3 có thể wrap thành transaction
 * thật (Room transaction + finally rollback file move).
 *
 * <p><b>Restore conflict</b>: fail-fast (throw) khi original path đã có file mới — user xử qua
 * Toast/Dialog. KHÔNG silent overwrite (user data preservation).
 */
@Singleton
public final class TrashOps {
    private static final String TRASH_DIR = ".AppTrash";
    private static final String FILES_SUBDIR = "files";

    private final TrashDao dao;

    @Inject
    public TrashOps(TrashDao dao) {
        this.dao = dao;
    }

    /**
     * Soft delete: move file vào trash + ghi Room entry. {@link FileOps#delete(VirtualNode)} là
     * hard delete (permanent).
     */
    public void moveToTrash(VirtualNode node) throws NodeException {
        if (!node.path().isLocal()) {
            throw new NodeException("Only local files can be moved to trash");
        }
        Path source = Path.of(node.path().path());
        if (!Files.exists(source)) {
            throw new NodeException("File no longer exists: " + node.name());
        }
        String id = UUID.randomUUID().toString();
        Path trashFileDir = trashRoot().resolve(FILES_SUBDIR).resolve(id);
        Path trashPath = trashFileDir.resolve(node.name());
        try {
            Files.createDirectories(trashFileDir);
            try {
                Files.move(source, trashPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(source, trashPath);
            }
        } catch (IOException e) {
            throw new NodeException("Move to trash failed: " + node.name(), e);
        }
        TrashEntryEntity entity = new TrashEntryEntity();
        entity.id = id;
        entity.originalPath = source.toString();
        entity.trashPath = trashPath.toString();
        entity.displayName = node.name();
        entity.deletedAtMillis = System.currentTimeMillis();
        entity.sizeBytes = node.isFolder() ? -1L : node.size();
        entity.directory = node.isFolder();
        dao.insert(entity);
    }

    /**
     * Khôi phục file về original path. Fail-fast nếu chỗ cũ đã có file mới (không silent overwrite).
     */
    public void restore(String entryId) throws NodeException {
        TrashEntryEntity entity = dao.findById(entryId);
        if (entity == null) {
            throw new NodeException("Trash entry not found: " + entryId);
        }
        Path trashPath = Path.of(entity.trashPath);
        Path originalPath = Path.of(entity.originalPath);
        if (Files.exists(originalPath)) {
            throw new NodeException("Cannot restore — destination exists: " + entity.displayName);
        }
        try {
            Path parent = originalPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.move(trashPath, originalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(trashPath, originalPath);
            }
            // dọn folder uuid trống sau khi move file ra
            deleteIfEmpty(trashPath.getParent());
        } catch (IOException e) {
            throw new NodeException("Restore failed: " + entity.displayName, e);
        }
        dao.deleteById(entryId);
    }

    /**
     * Xóa toàn bộ trash. FS-side recursive delete .AppTrash/files/ + clear Room.
     */
    public void emptyAll() throws NodeException {
        Path filesDir = trashRoot().resolve(FILES_SUBDIR);
        try {
            deleteRecursively(filesDir);
        } catch (IOException e) {
            throw new NodeException("Empty trash failed", e);
        }
        dao.deleteAll();
    }

    private static Path trashRoot() {
        return Path.of(StorageScope.storageRootFor(StorageScope.ROOT_PATH), TRASH_DIR);
    }

    private static void deleteIfEmpty(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            if (!stream.iterator().hasNext()) {
                Files.delete(dir);
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        if (Files.isDirectory(root) && !Files.isSymbolicLink(root)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(root);
    }
}

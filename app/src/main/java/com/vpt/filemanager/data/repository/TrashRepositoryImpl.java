package com.vpt.filemanager.data.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.json.JSONException;
import org.json.JSONObject;

import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.core.StorageScope;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.TrashEntry;
import com.vpt.filemanager.domain.repository.FileRepository;
import com.vpt.filemanager.domain.repository.TrashRepository;

/**
 * JSON-backed trash repository. The {@code LocalFileSystemProvider} already writes each deleted
 * item into {@code .AppTrash/files/{uuid}/{name}} plus a metadata sidecar at
 * {@code .AppTrash/info/{uuid}.json}; this repository reads that layout to list entries, restores
 * a single entry by moving the file back to its original path, and empties the whole trash.
 *
 * <p>KISS choice: no Room migration for v1 — the file-system layout is the single source of truth.
 * The Room {@code TrashEntryEntity} schema is kept for a future cleanup worker that needs indexed
 * {@code expires_at} queries (deferred).
 *
 * <p>Restore is intentionally fail-fast on destination collision: when the original parent now
 * holds a file with the same name, we throw {@link FileSystemException} so the UI can present a
 * conflict dialog rather than silently overwriting the user's data.
 */
@Singleton
public final class TrashRepositoryImpl implements TrashRepository {
    private static final String TRASH_DIR = ".AppTrash";
    private static final String INFO_SUBDIR = "info";
    private static final String FILES_SUBDIR = "files";

    private final FileRepository fileRepository;

    @Inject
    public TrashRepositoryImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public void moveToTrash(FilePath path) throws FileSystemException {
        // Delegate to the local provider — it already writes the file + sidecar JSON atomically.
        fileRepository.delete(path, false);
    }

    @Override
    public void restore(String entryId) throws FileSystemException {
        Path infoFile = infoFileFor(entryId);
        if (!Files.exists(infoFile)) {
            throw new FileSystemException("Trash entry not found: " + entryId);
        }
        try {
            JSONObject info = readJson(infoFile);
            Path trashPath = Path.of(info.optString("trashPath", ""));
            Path originalPath = Path.of(info.optString("originalPath", ""));
            if (trashPath.toString().isEmpty() || originalPath.toString().isEmpty()) {
                throw new FileSystemException("Trash entry metadata corrupt: " + entryId);
            }
            if (Files.exists(originalPath)) {
                throw new FileSystemException(
                        "Destination already exists, cannot restore: " + originalPath);
            }
            Path parent = originalPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.move(trashPath, originalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(trashPath, originalPath);
            }
            // Clean up the entry container + sidecar so the entry vanishes from listing.
            Files.deleteIfExists(infoFile);
            deleteIfEmpty(trashPath.getParent());
        } catch (IOException | JSONException e) {
            throw new FileSystemException("Failed to restore entry " + entryId, e);
        }
    }

    @Override
    public void empty() throws FileSystemException {
        Path trashRoot = trashRoot();
        if (!Files.exists(trashRoot)) {
            return;
        }
        try {
            deleteRecursively(trashRoot.resolve(FILES_SUBDIR));
            deleteRecursively(trashRoot.resolve(INFO_SUBDIR));
        } catch (IOException e) {
            throw new FileSystemException("Failed to empty trash", e);
        }
    }

    @Override
    public List<TrashEntry> entries() throws FileSystemException {
        Path infoDir = trashRoot().resolve(INFO_SUBDIR);
        if (!Files.isDirectory(infoDir)) {
            return Collections.emptyList();
        }
        List<TrashEntry> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(infoDir, "*.json")) {
            for (Path infoFile : stream) {
                TrashEntry entry = tryParseEntry(infoFile);
                if (entry != null) {
                    out.add(entry);
                }
            }
        } catch (IOException e) {
            throw new FileSystemException("Failed to list trash entries", e);
        }
        out.sort(Comparator.comparingLong((TrashEntry e) -> e.deletedAtMillis).reversed());
        return out;
    }

    private TrashEntry tryParseEntry(Path infoFile) {
        try {
            JSONObject json = readJson(infoFile);
            String id = json.optString("id", "");
            String originalPath = json.optString("originalPath", "");
            String trashPath = json.optString("trashPath", "");
            String displayName = json.optString("displayName", "");
            if (id.isEmpty() || originalPath.isEmpty() || trashPath.isEmpty()) {
                return null;
            }
            Path trashFile = Path.of(trashPath);
            boolean directory = Files.isDirectory(trashFile);
            long size = directory ? -1L : safeFileSize(trashFile);
            long deletedAt = json.optLong("deletedAt", infoFile.toFile().lastModified());
            return new TrashEntry(id, originalPath, displayName, trashPath, deletedAt, size, directory);
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    private static long safeFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static JSONObject readJson(Path file) throws IOException, JSONException {
        return new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    private static Path trashRoot() {
        return Path.of(StorageScope.storageRootFor(StorageScope.ROOT_PATH), TRASH_DIR);
    }

    private static Path infoFileFor(String entryId) {
        return trashRoot().resolve(INFO_SUBDIR).resolve(entryId + ".json");
    }

    /**
     * Remove the per-entry container folder {@code .AppTrash/files/{uuid}} after a successful
     * restore, but only if it ended up empty (a folder restore leaves nothing behind; a file
     * restore leaves the empty {uuid} dir).
     */
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

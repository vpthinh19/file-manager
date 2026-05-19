package com.vpt.filemanager.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.json.JSONException;
import org.json.JSONObject;

import dagger.hilt.android.qualifiers.ApplicationContext;

import com.vpt.filemanager.core.StorageScope;
import com.vpt.filemanager.core.error.FileSystemException;
import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.data.db.entity.TrashEntryEntity;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.TrashEntry;
import com.vpt.filemanager.domain.repository.FileRepository;
import com.vpt.filemanager.domain.repository.TrashRepository;

/**
 * Room-backed trash repository. Source of truth = {@link TrashDao}; the per-storage
 * {@code .AppTrash/files/{uuid}/{name}} layout still holds the actual moved files (since the
 * file-system provider is the one that physically moves them).
 *
 * <p>Legacy JSON-sidecar migration: pre-Phase 2C-5c installs wrote one
 * {@code .AppTrash/info/{uuid}.json} per trashed item. On first call to a read method, if a
 * one-shot SharedPreferences flag says we haven't migrated yet, we scan that directory, import
 * each entry into Room, and then delete the JSON files. The flag prevents repeated scans.
 *
 * <p>Restore is intentionally fail-fast on destination collision: if the original parent now
 * holds a file with the same name we throw {@link FileSystemException} so the UI can present a
 * conflict dialog rather than silently overwriting the user's data.
 */
@Singleton
public final class TrashRepositoryImpl implements TrashRepository {
    private static final String TRASH_DIR = ".AppTrash";
    private static final String INFO_SUBDIR = "info";
    private static final String FILES_SUBDIR = "files";
    private static final String PREFS_FILE = "trash_migration";
    private static final String KEY_MIGRATED = "json_migrated";

    private final FileRepository fileRepository;
    private final TrashDao dao;
    private final SharedPreferences prefs;
    private volatile boolean migrationChecked;

    @Inject
    public TrashRepositoryImpl(
            FileRepository fileRepository,
            TrashDao dao,
            @ApplicationContext Context ctx) {
        this.fileRepository = fileRepository;
        this.dao = dao;
        this.prefs = ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    @Override
    public void moveToTrash(FilePath path) throws FileSystemException {
        // Delegate to the local provider — it moves the file AND inserts the Room entry.
        fileRepository.delete(path, false);
    }

    @Override
    public void restore(String entryId) throws FileSystemException {
        ensureMigrated();
        TrashEntryEntity entity = dao.findById(entryId);
        if (entity == null) {
            throw new FileSystemException("Trash entry not found: " + entryId);
        }
        Path trashPath = Path.of(entity.trashPath);
        Path originalPath = Path.of(entity.originalPath);
        if (Files.exists(originalPath)) {
            throw new FileSystemException(
                    "Destination already exists, cannot restore: " + originalPath);
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
            deleteIfEmpty(trashPath.getParent());
        } catch (IOException e) {
            throw new FileSystemException("Failed to restore entry " + entryId, e);
        }
        dao.deleteById(entryId);
    }

    @Override
    public void empty() throws FileSystemException {
        ensureMigrated();
        Path trashRoot = trashRoot();
        if (Files.exists(trashRoot)) {
            try {
                deleteRecursively(trashRoot.resolve(FILES_SUBDIR));
                deleteRecursively(trashRoot.resolve(INFO_SUBDIR));
            } catch (IOException e) {
                throw new FileSystemException("Failed to empty trash", e);
            }
        }
        dao.deleteAll();
    }

    @Override
    public List<TrashEntry> entries() throws FileSystemException {
        ensureMigrated();
        return mapAll(dao.all());
    }

    @Override
    public LiveData<List<TrashEntry>> entriesLive() {
        // Trigger migration lazily; subsequent emits come straight from Room.
        ensureMigrated();
        return Transformations.map(dao.observeAll(), TrashRepositoryImpl::mapAll);
    }

    @NonNull
    private static List<TrashEntry> mapAll(@Nullable List<TrashEntryEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<TrashEntry> out = new ArrayList<>(rows.size());
        for (TrashEntryEntity e : rows) {
            out.add(new TrashEntry(
                    e.id, e.originalPath, e.displayName, e.trashPath,
                    e.deletedAtMillis, e.sizeBytes, e.directory));
        }
        return out;
    }

    // ---------- Legacy JSON migration ----------

    private void ensureMigrated() {
        if (migrationChecked) {
            return;
        }
        synchronized (this) {
            if (migrationChecked) {
                return;
            }
            if (!prefs.getBoolean(KEY_MIGRATED, false)) {
                importLegacyJsonsBestEffort();
                prefs.edit().putBoolean(KEY_MIGRATED, true).apply();
            }
            migrationChecked = true;
        }
    }

    /**
     * Scan {@code .AppTrash/info/*.json} (legacy sidecars). For each one, if Room doesn't yet have
     * a matching id, insert it. Then delete the JSON. Best-effort: a single bad file is skipped
     * rather than aborting the whole import.
     */
    private void importLegacyJsonsBestEffort() {
        Path infoDir = trashRoot().resolve(INFO_SUBDIR);
        if (!Files.isDirectory(infoDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(infoDir, "*.json")) {
            for (Path infoFile : stream) {
                TrashEntryEntity entity = tryParseJson(infoFile);
                if (entity != null && dao.findById(entity.id) == null) {
                    dao.insert(entity);
                }
                try {
                    Files.deleteIfExists(infoFile);
                } catch (IOException ignored) {
                }
            }
            // The info/ dir itself is no longer needed once empty.
            try {
                Files.deleteIfExists(infoDir);
            } catch (IOException ignored) {
            }
        } catch (IOException ignored) {
        }
    }

    @Nullable
    private static TrashEntryEntity tryParseJson(Path infoFile) {
        try {
            JSONObject json = new JSONObject(
                    new String(Files.readAllBytes(infoFile), StandardCharsets.UTF_8));
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
            TrashEntryEntity entity = new TrashEntryEntity();
            entity.id = id;
            entity.originalPath = originalPath;
            entity.trashPath = trashPath;
            entity.displayName = displayName.isEmpty() ? trashFile.getFileName().toString() : displayName;
            entity.deletedAtMillis = deletedAt;
            entity.sizeBytes = size;
            entity.directory = directory;
            return entity;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    // ---------- helpers ----------

    private static long safeFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
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

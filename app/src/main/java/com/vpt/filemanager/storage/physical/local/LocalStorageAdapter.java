package com.vpt.filemanager.storage.physical.local;

import android.content.Context;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.InvalidationSubscription;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/** The only adapter performing ordinary physical filesystem reads and mutations. */
@Singleton
public final class LocalStorageAdapter {
    private static final int WATCH_EVENTS = FileObserver.CREATE | FileObserver.DELETE
            | FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE
            | FileObserver.DELETE_SELF | FileObserver.MOVE_SELF;
    private static final long INVALIDATION_DEBOUNCE_MILLIS = 350L;

    private final File root;
    private final File workRoot;
    @Nullable private Handler main;
    private final Map<String, WatchGroup> watchers = new HashMap<>();

    @Inject
    public LocalStorageAdapter(@ApplicationContext Context context) {
        this(Environment.getExternalStorageDirectory(),
                new File(context.getCacheDir(), "physical-work"));
        clearWorkArea();
    }

    /** Alternate root used by host-side tests. */
    public LocalStorageAdapter(@NonNull File root) {
        this(root, new File(root, ".physical-work"));
    }

    private LocalStorageAdapter(@NonNull File root, @NonNull File workRoot) {
        this.root = root;
        this.workRoot = workRoot;
    }

    @NonNull
    public File resolve(@NonNull Path path) throws FileOperationException {
        if (!path.isStorage() || path.isInsideArchive()) {
            throw new FileOperationException("Path does not identify one physical file");
        }
        File file = fileAtStoragePath(path.storagePath());
        if (!file.exists()) throw new FileOperationException("Path not found: " + file);
        return file;
    }

    /** Maps a virtual storage path to its raw physical file. */
    @NonNull
    public File fileAtStoragePath(@NonNull String virtualPath) {
        return virtualPath.isEmpty() ? root : new File(root, virtualPath.substring(1));
    }

    /** Maps a physical child read by this adapter back to its virtual pane path. */
    @NonNull
    public Path pathOf(@NonNull File file) throws FileOperationException {
        String base = root.getAbsolutePath().replace('\\', '/');
        String target = file.getAbsolutePath().replace('\\', '/');
        if (target.equals(base)) return Path.storageRoot();
        if (!target.startsWith(base + "/")) {
            throw new FileOperationException("File is outside storage: " + file);
        }
        return Path.storage(target.substring(base.length()));
    }

    @NonNull
    public File rootDirectory() {
        return root;
    }

    @NonNull
    public List<File> children(@NonNull File directory) throws FileOperationException {
        if (!directory.isDirectory()) throw new FileOperationException("Not a directory: " + directory);
        File[] files = directory.listFiles();
        if (files == null) throw new FileOperationException("Unable to list: " + directory);
        List<File> result = new ArrayList<>(files.length);
        for (File file : files) result.add(file);
        return result;
    }

    @NonNull
    public File create(@NonNull File parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        File target = child(parent, name);
        try {
            boolean created = folder ? target.mkdir() : target.createNewFile();
            if (!created) throw new FileOperationException("Name already exists: " + name);
            return target;
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot create: " + name, error);
        }
    }

    public void rename(@NonNull File source, @NonNull String newName) throws FileOperationException {
        move(source, child(source.getParentFile(), newName));
    }

    public void deletePermanently(@NonNull File source) throws FileOperationException {
        try {
            deleteRecursively(source.toPath());
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot delete: " + source.getName(), error);
        }
    }

    public void copy(@NonNull File source, @NonNull File destination) throws FileOperationException {
        try {
            copyRecursively(source.toPath(), destination.toPath());
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot copy: " + source.getName(), error);
        }
    }

    public void move(@NonNull File source, @NonNull File destination) throws FileOperationException {
        try {
            movePath(source.toPath(), destination.toPath());
        } catch (IOException directFailure) {
            copy(source, destination);
            deletePermanently(source);
        }
    }

    @NonNull
    public InputStream openRead(@NonNull File source) throws FileOperationException {
        try {
            return Files.newInputStream(source.toPath(), StandardOpenOption.READ);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Unable to read: " + source, error);
        }
    }

    @NonNull
    public OutputStream openWrite(@NonNull File target) throws FileOperationException {
        try {
            return Files.newOutputStream(target.toPath(), StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Unable to write: " + target, error);
        }
    }

    public boolean exists(@NonNull File file) {
        return file.exists();
    }

    public void copyReplacing(@NonNull File source, @NonNull File destination)
            throws FileOperationException {
        if (source.isDirectory() && destination.isDirectory()) {
            mergeReplacing(source, destination);
            return;
        }
        File staged = createTemporarySibling(destination);
        try {
            deletePermanently(staged);
            copy(source, staged);
            if (exists(destination)) deletePermanently(destination);
            move(staged, destination);
        } finally {
            if (exists(staged)) deletePermanently(staged);
        }
    }

    public void moveReplacing(@NonNull File source, @NonNull File destination)
            throws FileOperationException {
        copyReplacing(source, destination);
        deletePermanently(source);
    }

    @NonNull
    public File fromAbsolutePath(@NonNull String absolutePath) {
        return new File(absolutePath);
    }

    public boolean isFile(@NonNull File file) {
        return file.isFile();
    }

    @NonNull
    public String name(@NonNull File file) {
        return file.getName();
    }

    @NonNull
    public String absolutePath(@NonNull File file) {
        return file.getAbsolutePath().replace('\\', '/');
    }

    @NonNull
    public File parent(@NonNull File file) throws FileOperationException {
        File parent = file.getParentFile();
        if (parent == null) throw new FileOperationException("File has no parent: " + file);
        return parent;
    }

    @NonNull
    public File safeDescendant(@NonNull File root, @NonNull String relative)
            throws FileOperationException {
        try {
            File destination = new File(root, relative).getCanonicalFile();
            String base = root.getCanonicalPath();
            String target = destination.getCanonicalPath();
            if (!target.equals(base) && !target.startsWith(base + File.separator)) {
                throw new FileOperationException("Archive contains an unsafe entry path");
            }
            return destination;
        } catch (IOException error) {
            throw new FileOperationException("Cannot resolve destination", error);
        }
    }

    @NonNull
    public File target(@NonNull File parent, @NonNull String name) throws FileOperationException {
        return child(parent, name);
    }

    @NonNull
    public File workFile(@NonNull String category, @NonNull String identity,
                         @NonNull String displayName) throws FileOperationException {
        File directory = new File(workRoot, category);
        ensureDirectory(directory);
        String safeName = displayName.replace('/', '_').replace('\\', '_');
        return new File(directory, Integer.toHexString(identity.hashCode()) + "-" + safeName);
    }

    @NonNull
    public File createTemporarySibling(@NonNull File original) throws FileOperationException {
        File parent = original.getParentFile();
        if (parent == null) throw new FileOperationException("File has no parent: " + original);
        try {
            return File.createTempFile("." + original.getName(), ".rewrite", parent);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot prepare replacement: " + original.getName(), error);
        }
    }

    public void ensureDirectory(@NonNull File directory) throws FileOperationException {
        if (directory.isDirectory()) return;
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new FileOperationException("Cannot create directory: " + directory);
        }
    }

    @NonNull
    public OutputStream openCreateWrite(@NonNull File target) throws FileOperationException {
        try {
            File parent = target.getParentFile();
            if (parent != null) ensureDirectory(parent);
            return Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Unable to write: " + target, error);
        }
    }

    public void replace(@NonNull File temporary, @NonNull File original)
            throws FileOperationException {
        try {
            try {
                Files.move(temporary.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException noAtomicMove) {
                Files.move(temporary.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException error) {
            throw new FileOperationException("Cannot replace: " + original.getName(), error);
        }
    }

    public long size(@NonNull File file) throws FileOperationException {
        if (!file.exists()) throw new FileOperationException("Path not found: " + file);
        return file.length();
    }

    public long modifiedAt(@NonNull File file) throws FileOperationException {
        if (!file.exists()) throw new FileOperationException("Path not found: " + file);
        return file.lastModified();
    }

    public boolean isDirectory(@NonNull File file) {
        return file.isDirectory();
    }

    private void clearWorkArea() {
        if (!workRoot.exists()) return;
        try {
            deleteRecursively(workRoot.toPath());
        } catch (IOException ignored) {
            // A stale work file is replaced when it is requested.
        }
    }

    /**
     * Shares one debounced physical watcher for every directory observed by one or more virtual
     * locations. This prevents dual panes showing the same folder from multiplying refresh work.
     */
    @NonNull
    public InvalidationSubscription observeDirectory(@NonNull File directory,
                                                      @NonNull Runnable invalidated)
            throws FileOperationException {
        if (!directory.isDirectory()) throw new FileOperationException("Not a directory: " + directory);
        String key = directory.getAbsolutePath();
        synchronized (watchers) {
            WatchGroup group = watchers.get(key);
            if (group == null) {
                group = new WatchGroup(directory);
                watchers.put(key, group);
                group.observer.startWatching();
            }
            group.callbacks.add(invalidated);
        }
        return () -> removeObserver(key, invalidated);
    }

    private void removeObserver(String key, Runnable callback) {
        synchronized (watchers) {
            WatchGroup group = watchers.get(key);
            if (group == null) return;
            group.callbacks.remove(callback);
            if (!group.callbacks.isEmpty()) return;
            group.observer.stopWatching();
            mainHandler().removeCallbacks(group.dispatch);
            watchers.remove(key);
        }
    }

    private final class WatchGroup {
        private final Set<Runnable> callbacks = new LinkedHashSet<>();
        private final Runnable dispatch = () -> {
            List<Runnable> snapshot;
            synchronized (watchers) {
                snapshot = new ArrayList<>(callbacks);
            }
            for (Runnable callback : snapshot) callback.run();
        };
        private final FileObserver observer;

        WatchGroup(File directory) {
            observer = new FileObserver(directory, WATCH_EVENTS) {
                @Override
                public void onEvent(int event, @Nullable String name) {
                    mainHandler().removeCallbacks(dispatch);
                    mainHandler().postDelayed(dispatch, INVALIDATION_DEBOUNCE_MILLIS);
                }
            };
        }
    }

    private Handler mainHandler() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static File child(File parent, String name) throws FileOperationException {
        if (parent == null || name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
            throw new FileOperationException("Invalid name");
        }
        return new File(parent, name.trim());
    }

    private void mergeReplacing(File source, File destination) throws FileOperationException {
        for (File sourceChild : children(source)) {
            File target = target(destination, sourceChild.getName());
            if (exists(target)) copyReplacing(sourceChild, target);
            else copy(sourceChild, target);
        }
    }

    private static void copyRecursively(java.nio.file.Path source, java.nio.file.Path destination) throws IOException {
        if (Files.isDirectory(source) && !Files.isSymbolicLink(source)) {
            Files.createDirectory(destination);
            try (DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(source)) {
                for (java.nio.file.Path child : stream) copyRecursively(child, destination.resolve(child.getFileName()));
            }
        } else {
            Files.copy(source, destination);
        }
    }

    private static void deleteRecursively(java.nio.file.Path path) throws IOException {
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
            try (DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(path)) {
                for (java.nio.file.Path child : stream) deleteRecursively(child);
            }
        }
        Files.deleteIfExists(path);
    }

    private static void movePath(java.nio.file.Path source, java.nio.file.Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, destination);
        }
    }
}

package com.vpt.filemanager.storage.facade;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.error.NameConflictException;
import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.handler.OpenResult;
import com.vpt.filemanager.storage.physical.local.LocalStorageAdapter;
import com.vpt.filemanager.storage.virtual.InvalidationSubscription;
import com.vpt.filemanager.storage.virtual.Storage;
import com.vpt.filemanager.storage.virtual.StorageRegistry;
import com.vpt.filemanager.storage.virtual.archive.ArchiveStorage;
import com.vpt.filemanager.storage.virtual.bookmarks.BookmarkCollection;
import com.vpt.filemanager.storage.virtual.trash.TrashCollection;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/** The only file-system API consumed by browser components. */
@Singleton
public final class StorageFacade {
    private final StorageRegistry registry;
    private final PathResolver resolver;
    private final TrashCollection trash;
    private final BookmarkCollection bookmarks;
    private final Context context;
    private final LocalStorageAdapter physical;

    @Inject
    public StorageFacade(StorageRegistry registry, PathResolver resolver,
                         TrashCollection trash, BookmarkCollection bookmarks,
                         @ApplicationContext Context context, LocalStorageAdapter physical) {
        this.registry = registry;
        this.resolver = resolver;
        this.trash = trash;
        this.bookmarks = bookmarks;
        this.context = context;
        this.physical = physical;
    }

    @NonNull
    public OpenResult open(@NonNull Path path) throws FileOperationException {
        return open(path, null);
    }

    /**
     * Routes the path to its backend, then lets the resolved
     * {@link com.vpt.filemanager.handler.Handler} open it. A non-null {@code as} forces the type
     * ("open as"); null resolves by extension.
     */
    @NonNull
    public OpenResult open(@NonNull Path path, @Nullable ExtensionRegistry.Type as)
            throws FileOperationException {
        Storage storage = registry.storageFor(path);
        return resolver.resolve(path, storage, as).open(path, storage);
    }

    @NonNull
    public InvalidationSubscription observe(@NonNull Path location, @NonNull Runnable invalidated)
            throws FileOperationException {
        return registry.storageFor(location).observe(location, invalidated);
    }

    public void create(@NonNull Path parent, @NonNull String name, boolean folder)
            throws FileOperationException {
        validName(name);
        Storage storage = registry.storageFor(parent);
        if (!storage.canWrite(parent)) throw new FileOperationException("Path is read-only");
        storage.create(parent, name.trim(), folder);
    }

    public void rename(@NonNull Entry entry, @NonNull String name) throws FileOperationException {
        validName(name);
        registry.storageFor(entry.path()).rename(entry, name.trim());
    }

    public void delete(@NonNull List<Entry> selected) throws FileOperationException {
        for (Entry entry : selected) {
            if (entry.isParent()) continue;
            if (entry.isInsideArchive() || entry.isTrashItem()) {
                registry.storageFor(entry.path()).delete(List.of(entry));
            } else {
                trash.put(entry);
            }
        }
    }

    public void restore(@NonNull List<Entry> selected) throws FileOperationException {
        for (Entry entry : selected) {
            if (entry.recordId() != null) trash.restore(entry.recordId());
        }
    }

    public void emptyTrash() throws FileOperationException {
        trash.empty();
    }

    public void bookmark(@NonNull Entry entry) throws FileOperationException {
        bookmarks.add(entry);
    }

    public void removeBookmarks(@NonNull List<Entry> selected) {
        for (Entry entry : selected) bookmarks.remove(entry);
    }

    public void transfer(@NonNull Entry source, @NonNull Path destination, boolean move,
                         @NonNull TransferDecision decision)
            throws FileOperationException {
        if (decision == TransferDecision.CANCEL || source.isParent()) return;
        Storage destinationStorage = registry.storageFor(destination);
        if (!destinationStorage.canWrite(destination)) {
            throw new FileOperationException("Destination is read-only");
        }
        Placement placement = resolvePlacement(destinationStorage, destination, source.name(), decision);
        place(source, registry.storageFor(source.path()), destinationStorage, destination, placement, move);
    }

    /** Decides the final name and whether to overwrite, honouring the conflict {@code decision}. */
    private Placement resolvePlacement(Storage destinationStorage, Path destination, String name,
                                       TransferDecision decision) throws FileOperationException {
        if (!hasName(destinationStorage, destination, name)) return new Placement(name, false);
        return switch (decision) {
            case ASK -> throw new NameConflictException(name);
            case REPLACE -> new Placement(name, true);
            case KEEP_BOTH -> new Placement(uniqueName(destinationStorage, destination, name), false);
            case CANCEL -> throw new IllegalStateException("Cancel is handled before placement");
        };
    }

    /** Carries the source between backends, picking the right strategy for the storage pair. */
    private void place(Entry source, Storage sourceStorage, Storage destinationStorage,
                       Path destination, Placement placement, boolean move)
            throws FileOperationException {
        String name = placement.name();
        boolean replace = placement.replace();
        if (sourceStorage == destinationStorage) {
            copyOrMove(destinationStorage, source, destination, name, replace, move);
        } else if (destinationStorage instanceof ArchiveStorage archive) {
            archive.importEntry(destination, source, name, replace);
            if (move) sourceStorage.delete(List.of(source));
        } else if (sourceStorage instanceof ArchiveStorage archive) {
            archive.extractToDevice(source, destination, name, replace);
            if (move) sourceStorage.delete(List.of(source));
        } else {
            copyOrMove(destinationStorage, source, destination, name, replace, move);
        }
    }

    private static void copyOrMove(Storage storage, Entry source, Path destination, String name,
                                   boolean replace, boolean move) throws FileOperationException {
        if (move) storage.moveInternal(source, destination, name, replace);
        else storage.copyInternal(source, destination, name, replace);
    }

    private record Placement(String name, boolean replace) { }

    @NonNull
    public String materializeIfRequired(@NonNull Entry entry) throws FileOperationException {
        if (entry.isInsideArchive()) {
            return physical.absolutePath(registry.storageFor(entry.path()).materialize(entry.path()));
        }
        return entry.localPath();
    }

    @NonNull
    public Uri contentUri(@NonNull Entry entry) throws FileOperationException {
        return contentUri(materializeIfRequired(entry));
    }

    @NonNull
    public Uri contentUri(@NonNull String localPath) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider",
                physical.fromAbsolutePath(localPath));
    }

    /** Physical location rendered to the user while {@link Path} remains a navigation address. */
    @NonNull
    public String displayPath(@NonNull Path location) {
        if (!location.isStorage()) return location.serialize();
        StringBuilder result = new StringBuilder(physical.absolutePath(
                physical.fileAtStoragePath(location.storagePath())));
        for (String inner : location.archivePaths()) result.append('!').append(inner);
        return result.toString();
    }

    private String uniqueName(Storage storage, Path destination, String name)
            throws FileOperationException {
        if (!hasName(storage, destination, name)) return name;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        int index = 1;
        String candidate;
        do {
            candidate = stem + " (" + index++ + ")" + suffix;
        } while (hasName(storage, destination, candidate));
        return candidate;
    }

    private static boolean hasName(Storage storage, Path destination, String name)
            throws FileOperationException {
        for (Entry entry : storage.list(destination)) {
            if (!entry.isParent() && entry.name().equals(name)) return true;
        }
        return false;
    }

    private static void validName(String value) throws FileOperationException {
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\")) {
            throw new FileOperationException("Invalid name");
        }
    }
}

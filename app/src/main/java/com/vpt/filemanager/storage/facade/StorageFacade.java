package com.vpt.filemanager.storage.facade;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.error.NameConflictException;
import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.handler.HandlerRegistry;
import com.vpt.filemanager.handler.HandlerResult;
import com.vpt.filemanager.storage.physical.local.LocalStorageAdapter;
import com.vpt.filemanager.storage.virtual.InvalidationSubscription;
import com.vpt.filemanager.storage.virtual.Storage;
import com.vpt.filemanager.storage.virtual.StorageRegistry;
import com.vpt.filemanager.storage.virtual.archive.ArchiveStorage;
import com.vpt.filemanager.storage.virtual.bookmarks.BookmarkCollection;
import com.vpt.filemanager.storage.virtual.trash.TrashCollection;

import java.io.File;
import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/** The only file-system API consumed by browser components. */
@Singleton
public final class StorageFacade {
    private final StorageRegistry registry;
    private final HandlerRegistry handlers;
    private final ExtensionRegistry extensions;
    private final TrashCollection trash;
    private final BookmarkCollection bookmarks;
    private final Context context;
    private final LocalStorageAdapter physical;

    @Inject
    public StorageFacade(StorageRegistry registry, HandlerRegistry handlers,
                         ExtensionRegistry extensions, TrashCollection trash,
                         BookmarkCollection bookmarks,
                         @ApplicationContext Context context, LocalStorageAdapter physical) {
        this.registry = registry;
        this.handlers = handlers;
        this.extensions = extensions;
        this.trash = trash;
        this.bookmarks = bookmarks;
        this.context = context;
        this.physical = physical;
    }

    @NonNull
    public OpenResult open(@NonNull Path path) throws FileOperationException {
        return open(path, OpenMode.DEFAULT);
    }

    @NonNull
    public OpenResult open(@NonNull Path path, @NonNull OpenMode mode)
            throws FileOperationException {
        Storage storage = registry.storageFor(path);
        if (storage.isContainer(path)) return directory(path, storage);
        File materialized = storage.materialize(path);
        ExtensionRegistry.Kind kind = mode == OpenMode.DEFAULT
                ? extensions.classify(fileName(path, materialized)) : explicit(mode);
        if (kind == ExtensionRegistry.Kind.OPEN_AS) return new OpenResult.NeedsOpenAs(path);
        if (kind == ExtensionRegistry.Kind.ARCHIVE) {
            Path mounted = path.mountArchive();
            Storage archive = registry.storageFor(mounted);
            if (!archive.isContainer(mounted)) throw new FileOperationException("Invalid archive");
            return directory(mounted, archive);
        }
        ContentType type = switch (kind) {
            case TEXT -> ContentType.TEXT;
            case IMAGE -> ContentType.IMAGE;
            case AUDIO -> ContentType.AUDIO;
            case VIDEO -> ContentType.VIDEO;
            case APK_INSTALLER, EXTERNAL -> ContentType.OTHER;
            case ARCHIVE, OPEN_AS -> throw new IllegalStateException("Routing was not resolved");
        };
        HandlerResult result = handlers.handlerFor(type).handle(materialized, path);
        if (type == ContentType.TEXT && result instanceof HandlerResult.OpenContent content) {
            boolean readOnly = path.isInsideArchive() && !storage.canWrite(path);
            result = new HandlerResult.OpenContent(content.source(), content.localPath(),
                    content.displayName(), content.type(), readOnly);
        }
        return new OpenResult.Content(result);
    }

    @NonNull
    public InvalidationSubscription observe(@NonNull Path location, @NonNull Runnable invalidated)
            throws FileOperationException {
        return registry.storageFor(location).observe(location, invalidated);
    }

    @NonNull
    public EnumSet<Capability> capabilities(@NonNull Path location) {
        EnumSet<Capability> result = EnumSet.of(Capability.COPY_OUT, Capability.OPEN_WITH,
                Capability.SHARE);
        try {
            if (registry.storageFor(location).canWrite(location)) {
                result.addAll(EnumSet.of(Capability.CREATE, Capability.RENAME, Capability.DELETE,
                        Capability.MOVE_IN, Capability.MOVE_OUT, Capability.EDIT_CONTENT));
            }
        } catch (FileOperationException ignored) {
            // Read-only actions remain available when capability probing cannot resolve a target.
        }
        return result;
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
        if (decision == TransferDecision.CANCEL) return;
        Storage destinationStorage = registry.storageFor(destination);
        if (!destinationStorage.canWrite(destination)) {
            throw new FileOperationException("Destination is read-only");
        }
        if (source.isParent()) return;
        Storage sourceStorage = registry.storageFor(source.path());
        boolean conflicts = hasName(destinationStorage, destination, source.name());
        if (conflicts && decision == TransferDecision.ASK) {
            throw new NameConflictException(source.name());
        }
        boolean replace = conflicts && decision == TransferDecision.REPLACE;
        String name = conflicts && decision == TransferDecision.KEEP_BOTH
                ? uniqueName(destinationStorage, destination, source.name()) : source.name();
        if (sourceStorage == destinationStorage) {
            if (move) sourceStorage.moveInternal(source, destination, name, replace);
            else sourceStorage.copyInternal(source, destination, name, replace);
        } else if (destinationStorage instanceof ArchiveStorage archiveDestination) {
            archiveDestination.importEntry(destination, source, name, replace);
            if (move) sourceStorage.delete(List.of(source));
        } else if (sourceStorage instanceof ArchiveStorage archiveSource) {
            archiveSource.extractToDevice(source, destination, name, replace);
            if (move) sourceStorage.delete(List.of(source));
        } else {
            if (move) destinationStorage.moveInternal(source, destination, name, replace);
            else destinationStorage.copyInternal(source, destination, name, replace);
        }
    }

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

    @NonNull
    private OpenResult.Directory directory(Path path, Storage storage) throws FileOperationException {
        return new OpenResult.Directory(path, storage.list(path), capabilities(path));
    }

    private String fileName(Path path, File materialized) {
        if (path.isInsideArchive()) {
            String inner = path.archiveInnerPath();
            int slash = inner.lastIndexOf('/');
            return slash < 0 ? inner : inner.substring(slash + 1);
        }
        return physical.name(materialized);
    }

    private static ExtensionRegistry.Kind explicit(OpenMode mode) {
        return switch (mode) {
            case TEXT -> ExtensionRegistry.Kind.TEXT;
            case IMAGE -> ExtensionRegistry.Kind.IMAGE;
            case AUDIO -> ExtensionRegistry.Kind.AUDIO;
            case VIDEO -> ExtensionRegistry.Kind.VIDEO;
            case ARCHIVE -> ExtensionRegistry.Kind.ARCHIVE;
            case DEFAULT -> throw new IllegalStateException("DEFAULT mode is implicit");
        };
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

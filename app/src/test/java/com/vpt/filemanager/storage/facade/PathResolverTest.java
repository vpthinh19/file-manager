package com.vpt.filemanager.storage.facade;

import static org.junit.Assert.assertEquals;

import com.vpt.filemanager.core.format.ExtensionRegistry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.handler.ArchiveHandler;
import com.vpt.filemanager.handler.FolderHandler;
import com.vpt.filemanager.handler.HandlerRegistry;
import com.vpt.filemanager.handler.ImageHandler;
import com.vpt.filemanager.handler.OpenAsHandler;
import com.vpt.filemanager.handler.OtherHandler;
import com.vpt.filemanager.handler.TextHandler;
import com.vpt.filemanager.storage.virtual.FakeStorage;
import com.vpt.filemanager.storage.virtual.Storage;
import com.vpt.filemanager.storage.virtual.StorageRegistry;

import org.junit.Test;

import java.util.Set;

/** The resolver picks a handler from the path alone (plus the storage's container flag). */
public final class PathResolverTest {

    private static PathResolver resolver() {
        HandlerRegistry handlers = new HandlerRegistry(Set.of(
                new FolderHandler(),
                new ArchiveHandler(new StorageRegistry(Set.of())),
                new TextHandler(),
                new ImageHandler(),
                new OpenAsHandler()), new OtherHandler());
        return new PathResolver(new ExtensionRegistry(), handlers);
    }

    private static FakeStorage device(boolean container) {
        FakeStorage storage = new FakeStorage(p -> p.isStorage() && !p.isInsideArchive());
        storage.container = container;
        return storage;
    }

    private static ExtensionRegistry.Type resolved(Path path, Storage storage, OpenMode mode)
            throws Exception {
        return resolver().resolve(path, storage, mode).type();
    }

    @Test
    public void containerResolvesToFolderHandler() throws Exception {
        assertEquals(ExtensionRegistry.Type.FOLDER,
                resolved(Path.storage("/docs"), device(true), OpenMode.DEFAULT));
    }

    @Test
    public void recognisedExtensionResolvesToContentHandler() throws Exception {
        assertEquals(ExtensionRegistry.Type.TEXT,
                resolved(Path.storage("/docs/a.txt"), device(false), OpenMode.DEFAULT));
    }

    @Test
    public void archiveExtensionResolvesToArchiveHandler() throws Exception {
        assertEquals(ExtensionRegistry.Type.ARCHIVE,
                resolved(Path.storage("/bundle.zip"), device(false), OpenMode.DEFAULT));
    }

    @Test
    public void unknownExtensionResolvesToOpenAsHandler() throws Exception {
        assertEquals(ExtensionRegistry.Type.OPEN_AS,
                resolved(Path.storage("/docs/README"), device(false), OpenMode.DEFAULT));
    }

    @Test
    public void entryInsideArchiveIsClassifiedByItsInnerName() throws Exception {
        FakeStorage archive = new FakeStorage(Path::isInsideArchive);
        assertEquals(ExtensionRegistry.Type.TEXT,
                resolved(Path.archive("/bundle.zip", "/notes.txt"), archive, OpenMode.DEFAULT));
    }

    @Test
    public void explicitModeOverridesExtension() throws Exception {
        assertEquals(ExtensionRegistry.Type.IMAGE,
                resolved(Path.storage("/docs/a.txt"), device(false), OpenMode.IMAGE));
    }
}

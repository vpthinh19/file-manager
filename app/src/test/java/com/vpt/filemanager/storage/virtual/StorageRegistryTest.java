package com.vpt.filemanager.storage.virtual;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;

import org.junit.Test;

import java.util.Set;

public final class StorageRegistryTest {
    @Test
    public void routesEachPathToTheStorageThatHandlesIt() throws Exception {
        FakeStorage local = new FakeStorage(p -> p.isStorage() && !p.isInsideArchive());
        FakeStorage archive = new FakeStorage(Path::isInsideArchive);
        FakeStorage trash = new FakeStorage(Path::isTrash);
        StorageRegistry registry = new StorageRegistry(Set.of(local, archive, trash));

        assertSame(local, registry.storageFor(Path.storage("/docs/a.txt")));
        assertSame(archive, registry.storageFor(Path.archive("/a.zip", "/inner.txt")));
        assertSame(trash, registry.storageFor(Path.trash()));
    }

    @Test
    public void throwsWhenNoStorageClaimsThePath() {
        StorageRegistry registry = new StorageRegistry(Set.of());
        assertThrows(FileOperationException.class, () -> registry.storageFor(Path.storageRoot()));
    }
}

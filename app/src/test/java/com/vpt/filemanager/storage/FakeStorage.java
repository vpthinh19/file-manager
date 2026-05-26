package com.vpt.filemanager.storage;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.path.Path;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Predicate;

/**
 * Minimal {@link Storage} test double. Only the routing/dispatch surface the
 * registry and resolver tests exercise ({@code handles}, {@code isContainer},
 * {@code list}, {@code materialize}) is configurable; every mutating method
 * throws so a test that accidentally triggers one fails loudly.
 */
public final class FakeStorage implements Storage {
    private final Predicate<Path> handles;
    public boolean container;
    public List<Entry> entries = List.of();
    public File materialized;

    public FakeStorage(Predicate<Path> handles) {
        this.handles = handles;
    }

    @Override public boolean handles(Path path) { return handles.test(path); }
    @Override public boolean isContainer(Path path) { return container; }
    @Override public List<Entry> list(Path path) { return entries; }
    @Override public File materialize(Path path) { return materialized; }
    @Override public boolean canWrite(Path path) { return false; }

    @Override public void create(Path parent, String name, boolean folder)
            throws FileOperationException { throw unsupported(); }
    @Override public void rename(Entry entry, String newName)
            throws FileOperationException { throw unsupported(); }
    @Override public void delete(List<Entry> entries)
            throws FileOperationException { throw unsupported(); }
    @Override public void copyInternal(Entry source, Path destinationParent, String name)
            throws FileOperationException { throw unsupported(); }
    @Override public void moveInternal(Entry source, Path destinationParent, String name)
            throws FileOperationException { throw unsupported(); }
    @Override public InputStream openRead(Entry entry)
            throws FileOperationException { throw unsupported(); }
    @Override public OutputStream openWrite(Entry entry)
            throws FileOperationException { throw unsupported(); }

    private static FileOperationException unsupported() {
        return new FileOperationException("not supported by FakeStorage");
    }
}

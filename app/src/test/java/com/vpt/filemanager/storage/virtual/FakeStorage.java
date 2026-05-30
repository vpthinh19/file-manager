package com.vpt.filemanager.storage.virtual;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.path.Path;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/**
 * Minimal {@link Storage} test double. Only the routing/dispatch surface the registry and facade
 * tests exercise ({@code handles}, {@code isContainer}, {@code list}, {@code materialize}) is
 * configurable; the interface defaults make every mutation throw, so a test that accidentally
 * triggers one fails loudly.
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
}

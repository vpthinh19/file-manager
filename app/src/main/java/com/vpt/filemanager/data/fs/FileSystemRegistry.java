package com.vpt.filemanager.data.fs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.domain.model.FilePath;

@Singleton
public final class FileSystemRegistry {
    private final Map<String, FileSystemProvider> byScheme;

    @Inject
    public FileSystemRegistry(Set<FileSystemProvider> providers) {
        Map<String, FileSystemProvider> map = new HashMap<>();
        for (FileSystemProvider provider : providers) {
            map.put(provider.scheme(), provider);
        }
        byScheme = Collections.unmodifiableMap(map);
    }

    public FileSystemProvider providerFor(FilePath path) {
        FileSystemProvider provider = byScheme.get(path.scheme());
        if (provider == null) {
            throw new IllegalStateException("No provider for scheme: " + path.scheme());
        }
        return provider;
    }
}


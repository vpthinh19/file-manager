package com.vpt.filemanager.browser.action.properties;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.data.local.LocalStorageAdapter;
import com.vpt.filemanager.core.threading.AppExecutors;

@Singleton
public final class PropertiesService {
    private final LocalStorageAdapter files;
    private final PropertiesMetadataReader metadata;
    private final AppExecutors executors;

    @Inject
    public PropertiesService(LocalStorageAdapter files, PropertiesMetadataReader metadata,
                             AppExecutors executors) {
        this.files = files;
        this.metadata = metadata;
        this.executors = executors;
    }

    public PropertiesModel read(String path) throws FileOperationException {
        Item entry = files.inspect(path);
        return new PropertiesModel(path, entry.name(), parent(path), entry.isFolder(),
                entry.size(), entry.modifiedAt(), metadata.read(path));
    }

    public Future<Long> calculateFolderSize(String path) {
        return executors.computation().submit(() -> {
            long size = 0L;
            Deque<String> pending = new ArrayDeque<>();
            pending.push(path);
            while (!pending.isEmpty()) {
                try {
                    for (Item entry : files.list(pending.pop())) {
                        if (entry.isFolder()) {
                            pending.push(entry.localPath());
                        } else if (entry.size() > 0) {
                            size += entry.size();
                        }
                    }
                } catch (FileOperationException ignored) {
                }
            }
            return size;
        });
    }

    private static String parent(String path) {
        int separator = path.lastIndexOf('/');
        return separator <= 0 ? "/" : path.substring(0, separator);
    }
}

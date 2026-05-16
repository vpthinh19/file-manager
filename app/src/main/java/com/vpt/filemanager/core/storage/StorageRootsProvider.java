package com.vpt.filemanager.core.storage;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

@Singleton
public final class StorageRootsProvider {
    private static final String[] QUICK_LINKS = {
            "Download", "DCIM", "Documents", "Movies", "Music", "Pictures"
    };

    private final Context context;

    @Inject
    public StorageRootsProvider(@ApplicationContext Context context) {
        this.context = context;
    }

    public List<FileNode> discover() {
        List<FileNode> nodes = new ArrayList<>();
        addInternalAndExternal(nodes);
        addQuickLinks(nodes);
        return nodes;
    }

    private void addInternalAndExternal(List<FileNode> nodes) {
        StorageManager sm = context.getSystemService(StorageManager.class);
        if (sm == null) {
            File fallback = Environment.getExternalStorageDirectory();
            if (fallback != null) {
                nodes.add(rootNode("Internal Storage", fallback, StorageRoot.Kind.INTERNAL));
            }
            return;
        }
        for (StorageVolume volume : sm.getStorageVolumes()) {
            File directory = volume.getDirectory();
            if (directory == null) {
                continue;
            }
            String label = volume.isPrimary()
                    ? "Internal Storage"
                    : describe(volume);
            StorageRoot.Kind kind = volume.isPrimary()
                    ? StorageRoot.Kind.INTERNAL
                    : volume.isRemovable() ? StorageRoot.Kind.SD_CARD : StorageRoot.Kind.USB;
            nodes.add(rootNode(label, directory, kind));
        }
    }

    private void addQuickLinks(List<FileNode> nodes) {
        File primary = Environment.getExternalStorageDirectory();
        if (primary == null) {
            return;
        }
        for (String name : QUICK_LINKS) {
            File dir = new File(primary, name);
            if (dir.isDirectory()) {
                nodes.add(rootNode(name, dir, StorageRoot.Kind.QUICK_LINK));
            }
        }
    }

    private static StorageRootNode rootNode(String displayName, File directory, StorageRoot.Kind kind) {
        StorageRoot root = new StorageRoot(displayName, FilePath.local(directory.getAbsolutePath()), kind);
        long total = safeSpace(directory, true);
        long free = safeSpace(directory, false);
        return new StorageRootNode(root, total, free);
    }

    private static long safeSpace(File dir, boolean total) {
        try {
            return total ? dir.getTotalSpace() : dir.getUsableSpace();
        } catch (SecurityException e) {
            return -1;
        }
    }

    private static String describe(StorageVolume volume) {
        String desc = volume.getDescription(null);
        return desc == null || desc.isEmpty() ? "External Storage" : desc;
    }
}

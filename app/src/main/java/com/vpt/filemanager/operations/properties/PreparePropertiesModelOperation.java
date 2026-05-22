package com.vpt.filemanager.operations.properties;

import androidx.annotation.NonNull;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Prepare the UI-neutral Properties model for one virtual node.
 */
public final class PreparePropertiesModelOperation {
    public PropertiesModel execute(Input input) {
        VirtualNode node = input.node;
        NodePath path = node.path();
        String name = node.name();
        if ("/".equals(name)) {
            name = path.path();
        }
        String parent = parentDisplay(path);
        return new PropertiesModel(
                path,
                name,
                parent,
                node.isFolder(),
                node.size(),
                node.modifiedAt(),
                input.metadataReader.read(path));
    }

    @NonNull
    private static String parentDisplay(@NonNull NodePath path) {
        NodePath parent = path.parent();
        if (path.equals(parent)) {
            return "/";
        }
        return parent.path();
    }

    public static final class Input {
        @NonNull public final VirtualNode node;
        @NonNull public final PropertiesMetadataReader metadataReader;

        public Input(@NonNull VirtualNode node,
                     @NonNull PropertiesMetadataReader metadataReader) {
            this.node = node;
            this.metadataReader = metadataReader;
        }
    }
}

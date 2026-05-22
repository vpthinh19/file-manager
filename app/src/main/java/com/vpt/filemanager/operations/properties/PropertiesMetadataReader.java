package com.vpt.filemanager.operations.properties;

import androidx.annotation.Nullable;

import com.vpt.filemanager.node.NodePath;

/**
 * Source-specific metadata reader for properties details.
 */
public interface PropertiesMetadataReader {
    @Nullable
    PropertiesModel.PosixMetadata read(NodePath path);
}

package com.vpt.filemanager.browser.action.properties;

import androidx.annotation.Nullable;


public interface PropertiesMetadataReader {
    @Nullable
    PropertiesModel.PosixMetadata read(String localPath);
}

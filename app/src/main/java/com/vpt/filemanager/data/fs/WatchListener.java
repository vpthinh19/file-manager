package com.vpt.filemanager.data.fs;

import com.vpt.filemanager.domain.model.FilePath;

public interface WatchListener {
    void onChanged(FilePath path);
}


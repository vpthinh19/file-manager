package com.vpt.filemanager.component.content;

import androidx.annotation.Nullable;

import com.vpt.filemanager.core.detect.ContentType;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.component.pane.PaneId;

/** One file currently displayed in the full-screen content surface. */
public record OpenedContent(PaneId pane, Path source, String localPath, String displayName,
                            ContentType type, boolean readOnly,
                            @Nullable Path archiveEntry) {
}

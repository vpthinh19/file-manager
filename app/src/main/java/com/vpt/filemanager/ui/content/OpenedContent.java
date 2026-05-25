package com.vpt.filemanager.ui.content;

import androidx.annotation.Nullable;

import com.vpt.filemanager.content.ContentType;
import com.vpt.filemanager.navigation.Location;
import com.vpt.filemanager.ui.pane.PaneId;

/** One file currently displayed in the full-screen content surface. */
public record OpenedContent(PaneId pane, Location source, String localPath, String displayName,
                            ContentType type, boolean readOnly,
                            @Nullable Location archiveEntry) {
}

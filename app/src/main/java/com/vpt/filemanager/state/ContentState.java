package com.vpt.filemanager.state;

import androidx.annotation.Nullable;

import com.vpt.filemanager.model.ContentKind;
import com.vpt.filemanager.model.Location;

/** A resolved full-screen document/media resource displayed instead of the browser surface. */
public record ContentState(PaneId pane, Location source, String localPath, String displayName,
                           ContentKind kind, boolean readOnly,
                           @Nullable Location archiveEntry) {
}

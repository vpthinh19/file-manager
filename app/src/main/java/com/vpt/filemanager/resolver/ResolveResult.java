package com.vpt.filemanager.resolver;

import androidx.annotation.Nullable;

import com.vpt.filemanager.model.ContentKind;
import com.vpt.filemanager.model.Entry;
import com.vpt.filemanager.model.Location;

import java.util.List;

/** Output of resolving the current pane location. */
public interface ResolveResult {
    record Directory(List<Entry> entries) implements ResolveResult {
        public Directory {
            entries = List.copyOf(entries);
        }
    }

    record Content(Location source, String localPath, String displayName, ContentKind kind,
                   boolean readOnly, @Nullable Location archiveEntry) implements ResolveResult {
    }

    record ReplaceLocation(Location target) implements ResolveResult {
    }
}

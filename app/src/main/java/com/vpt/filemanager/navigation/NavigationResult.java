package com.vpt.filemanager.navigation;

import androidx.annotation.Nullable;

import com.vpt.filemanager.content.ContentType;
import com.vpt.filemanager.entry.Entry;

import java.util.List;

/** Outcome of opening a pane location: visible entries, full-screen content, or a mounted redirect. */
public interface NavigationResult {
    record Entries(List<Entry> entries) implements NavigationResult {
        public Entries {
            entries = List.copyOf(entries);
        }
    }

    record OpenContent(Location source, String localPath, String displayName, ContentType type,
                       boolean readOnly, @Nullable Location archiveEntry) implements NavigationResult {
    }

    record Redirect(Location target) implements NavigationResult {
    }
}

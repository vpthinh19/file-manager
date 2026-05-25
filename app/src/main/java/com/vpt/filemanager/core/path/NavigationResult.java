package com.vpt.filemanager.core.path;

import androidx.annotation.Nullable;

import com.vpt.filemanager.content.ContentType;
import com.vpt.filemanager.entry.Entry;

import java.util.List;

/**
 * Outcome of opening a pane path: visible entries, full-screen content, or a mounted redirect.
 *
 * <p>Phase 4 will replace this with {@code handler.HandlerResult}; the {@code Redirect}
 * variant disappears at that point because {@code ArchiveStorage.isContainer()} will
 * handle archive-file taps transparently.
 */
public interface NavigationResult {
    record Entries(List<Entry> entries) implements NavigationResult {
        public Entries {
            entries = List.copyOf(entries);
        }
    }

    record OpenContent(Path source, String localPath, String displayName, ContentType type,
                       boolean readOnly, @Nullable Path archiveEntry) implements NavigationResult {
    }

    record Redirect(Path target) implements NavigationResult {
    }
}

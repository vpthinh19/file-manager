package com.vpt.filemanager.handler;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.Capability;

import java.util.EnumSet;
import java.util.List;

/**
 * What a {@link Handler} produces when opening a path — and the value {@code StorageFacade.open}
 * hands back to a pane. One sealed result covers every handler outcome.
 */
public sealed interface OpenResult
        permits OpenResult.Directory, OpenResult.OpenContent,
                OpenResult.LaunchIntent, OpenResult.NeedsOpenAs {

    /** A directory listing to render in the pane. */
    record Directory(Path canonicalPath, List<Entry> entries, EnumSet<Capability> capabilities)
            implements OpenResult {
        public Directory {
            entries = List.copyOf(entries);
            capabilities = capabilities.clone();
        }
    }

    /** A file to show in an in-app viewer or editor. */
    record OpenContent(Path source, String localPath, String displayName, ContentType type,
                       boolean readOnly) implements OpenResult { }

    /** A file to hand to an external app via {@code ACTION_VIEW}. */
    record LaunchIntent(Path source, String localPath, String mimeType) implements OpenResult { }

    /** A file with no recognised type; the user must choose how to open it. */
    record NeedsOpenAs(Path source) implements OpenResult { }
}

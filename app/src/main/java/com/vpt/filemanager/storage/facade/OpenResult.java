package com.vpt.filemanager.storage.facade;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.handler.HandlerResult;

import java.util.EnumSet;
import java.util.List;

/** Result of opening a virtual path through {@link StorageFacade}. */
public sealed interface OpenResult permits OpenResult.Directory, OpenResult.Content,
        OpenResult.NeedsOpenAs {
    record Directory(Path canonicalPath, List<Entry> entries,
                     EnumSet<Capability> capabilities) implements OpenResult {
        public Directory {
            entries = List.copyOf(entries);
            capabilities = capabilities.clone();
        }
    }

    record Content(HandlerResult handled) implements OpenResult {
    }

    record NeedsOpenAs(Path source) implements OpenResult {
    }
}

package com.vpt.filemanager.handler;

import com.vpt.filemanager.core.detect.ContentType;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.entry.Entry;

import java.util.List;

/**
 * Outcome of opening a {@link Path}. Sealed so the resolver and components
 * pattern-match on a closed set of cases — no surprise variants.
 *
 * <ul>
 *   <li>{@link Entries} — a container produced a list to render in the pane.</li>
 *   <li>{@link OpenContent} — a file produced full-screen content (text, image,
 *       audio, video) the host fragment should show.</li>
 *   <li>{@link LaunchIntent} — the file is not viewable in-app; the component
 *       should fire {@code Intent.ACTION_VIEW} for the user's chosen app.</li>
 * </ul>
 */
public sealed interface HandlerResult
        permits HandlerResult.Entries, HandlerResult.OpenContent, HandlerResult.LaunchIntent {

    record Entries(List<Entry> entries) implements HandlerResult {
        public Entries {
            entries = List.copyOf(entries);
        }
    }

    record OpenContent(Path source, String localPath, String displayName,
                       ContentType type, boolean readOnly) implements HandlerResult {
    }

    record LaunchIntent(Path source, String localPath, String mimeType) implements HandlerResult {
    }
}

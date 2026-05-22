package com.vpt.filemanager.browser.constraint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Set;

import com.vpt.filemanager.node.FilePath;

/**
 * Immutable snapshot of workspace state used by {@link ActionConstraints} to compute the set of
 * disabled actions for the More bottom sheet.
 *
 * <p>Holds only primitives + immutable refs — no {@code VirtualNode}, no Android deps. Built at
 * click-time (when More sheet is about to show), passed once to {@link ActionConstraints#compute},
 * then discarded. Not designed to be cached across click events — selection state changes too fast.
 *
 * <p>Why pass primitives instead of the live {@code PaneViewModel}: keeps the constraint layer
 * JVM-testable (no Mockito, no Robolectric) and decouples rule-evaluation from UI lifecycle.
 */
public final class WorkspaceState {
    @NonNull public final Set<FilePath> selection;
    /** TRUE = single folder, FALSE = single file, null = multi-select or single with unknown type. */
    @Nullable public final Boolean singleIsFolder;
    @Nullable public final FilePath activePath;
    @Nullable public final FilePath inactivePath;

    private WorkspaceState(@NonNull Set<FilePath> selection,
                            @Nullable Boolean singleIsFolder,
                            @Nullable FilePath activePath,
                            @Nullable FilePath inactivePath) {
        this.selection = selection;
        this.singleIsFolder = singleIsFolder;
        this.activePath = activePath;
        this.inactivePath = inactivePath;
    }

    @NonNull
    public static WorkspaceState of(@Nullable Set<FilePath> selection,
                                     @Nullable Boolean singleIsFolder,
                                     @Nullable FilePath activePath,
                                     @Nullable FilePath inactivePath) {
        return new WorkspaceState(
                selection == null ? Collections.emptySet() : selection,
                singleIsFolder, activePath, inactivePath);
    }
}

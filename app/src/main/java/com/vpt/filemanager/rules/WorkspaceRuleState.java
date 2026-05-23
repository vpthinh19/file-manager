package com.vpt.filemanager.rules;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

import com.vpt.filemanager.node.NodePath;

/**
 * Immutable snapshot of workspace state used by {@link RuleEngine} to compute the set of
 * disabled actions for the More bottom sheet.
 *
 * <p>Holds only primitives + immutable refs — no {@code VirtualNode}, no Android deps. Built at
 * click-time (when an action is rendered or executed), passed to {@link RuleEngine#disabledActions},
 * then discarded. Not designed to be cached across click events — selection state changes too fast.
 *
 * <p>Why pass primitives instead of the live {@code PaneViewModel}: keeps the constraint layer
 * JVM-testable (no Mockito, no Robolectric) and decouples rule-evaluation from UI lifecycle.
 */
public final class WorkspaceRuleState {
    @NonNull public final Set<NodePath> selection;
    /** TRUE = single folder, FALSE = single file, null = multi-select or single with unknown type. */
    @Nullable public final Boolean singleIsFolder;
    @Nullable public final NodePath activePath;
    @Nullable public final NodePath inactivePath;

    private WorkspaceRuleState(@NonNull Set<NodePath> selection,
                            @Nullable Boolean singleIsFolder,
                            @Nullable NodePath activePath,
                            @Nullable NodePath inactivePath) {
        this.selection = selection;
        this.singleIsFolder = singleIsFolder;
        this.activePath = activePath;
        this.inactivePath = inactivePath;
    }

    @NonNull
    public static WorkspaceRuleState of(@Nullable Set<NodePath> selection,
                                     @Nullable Boolean singleIsFolder,
                                     @Nullable NodePath activePath,
                                     @Nullable NodePath inactivePath) {
        return new WorkspaceRuleState(
                selection == null ? Set.of() : Set.copyOf(selection),
                singleIsFolder, activePath, inactivePath);
    }
}

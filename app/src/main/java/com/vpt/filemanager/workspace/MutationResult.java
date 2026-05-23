package com.vpt.filemanager.workspace;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.vpt.filemanager.node.NodePath;

/**
 * Describes which virtual branches may have changed after an operation or an external signal.
 *
 * <p>The result does not mutate UI state. Workspace consumes it to invalidate materialized
 * snapshots and document sessions. External observers use the same contract after receiving a
 * filesystem/provider change notification.
 */
public final class MutationResult {
    @NonNull public final Set<NodePath> changedContainers;
    @NonNull public final Set<NodePath> removedSubtrees;
    public final boolean allLiveSnapshots;

    private MutationResult(@NonNull Set<NodePath> changedContainers,
                           @NonNull Set<NodePath> removedSubtrees,
                           boolean allLiveSnapshots) {
        this.changedContainers = Collections.unmodifiableSet(new LinkedHashSet<>(changedContainers));
        this.removedSubtrees = Collections.unmodifiableSet(new LinkedHashSet<>(removedSubtrees));
        this.allLiveSnapshots = allLiveSnapshots;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public static MutationResult allLiveSnapshots() {
        return new MutationResult(Collections.emptySet(), Collections.emptySet(), true);
    }

    public boolean affectsListing(@NonNull NodePath visibleContainer) {
        if (allLiveSnapshots || changedContainers.contains(visibleContainer)) {
            return true;
        }
        for (NodePath removed : removedSubtrees) {
            if (visibleContainer.isSameOrDescendantOf(removed)) {
                return true;
            }
        }
        return false;
    }

    public static final class Builder {
        private final Set<NodePath> changedContainers = new LinkedHashSet<>();
        private final Set<NodePath> removedSubtrees = new LinkedHashSet<>();

        @NonNull
        public Builder changedContainer(@NonNull NodePath path) {
            changedContainers.add(path);
            return this;
        }

        @NonNull
        public Builder removedSubtree(@NonNull NodePath path) {
            removedSubtrees.add(path);
            return this;
        }

        @NonNull
        public MutationResult build() {
            return new MutationResult(changedContainers, removedSubtrees, false);
        }
    }
}

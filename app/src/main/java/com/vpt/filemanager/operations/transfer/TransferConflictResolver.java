package com.vpt.filemanager.operations.transfer;

import androidx.annotation.NonNull;

import com.vpt.filemanager.node.NodeException;

/**
 * Strategy for resolving transfer name conflicts.
 *
 * <p>Implementations may be pure policy objects in tests/workers, or Android dialog bridges at the
 * browser boundary. The transfer operation only consumes the decision.
 */
public interface TransferConflictResolver {
    @NonNull
    TransferConflictDecision resolve(@NonNull NameConflict conflict) throws NodeException;
}

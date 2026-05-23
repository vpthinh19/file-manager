package com.vpt.filemanager.workspace;

import androidx.annotation.NonNull;

import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.bookmark.AddBookmarkOperation;
import com.vpt.filemanager.operations.bookmark.RemoveBookmarksOperation;
import com.vpt.filemanager.operations.create.CreateNodeOperation;
import com.vpt.filemanager.operations.create.ExistingNamePolicy;
import com.vpt.filemanager.operations.delete.DeleteNodesOperation;
import com.vpt.filemanager.operations.rename.RenameNodeOperation;
import com.vpt.filemanager.operations.transfer.TransferKind;
import com.vpt.filemanager.operations.transfer.TransferOperation;
import com.vpt.filemanager.operations.trash.EmptyTrashOperation;
import com.vpt.filemanager.operations.trash.RestoreTrashEntriesOperation;
import com.vpt.filemanager.rules.RuleEngine;
import com.vpt.filemanager.rules.WorkspaceRuleState;

/**
 * Workspace command boundary for mutating operations.
 *
 * <p>UI may describe intent and render availability, but it does not execute mutation rules or
 * derive reconciliation scope. Commands are validated here at execution time and each operation
 * publishes its explicit virtual-tree mutation through the store.
 */
@Singleton
public final class WorkspaceCommandDispatcher {
    private final WorkspaceStore store;
    private final CreateNodeOperation createNodeOperation;
    private final RenameNodeOperation renameNodeOperation;
    private final DeleteNodesOperation deleteNodesOperation;
    private final TransferOperation transferOperation;
    private final AddBookmarkOperation addBookmarkOperation;
    private final RemoveBookmarksOperation removeBookmarksOperation;
    private final RestoreTrashEntriesOperation restoreTrashEntriesOperation;
    private final EmptyTrashOperation emptyTrashOperation;
    private final RuleEngine rules;

    @Inject
    public WorkspaceCommandDispatcher(
            WorkspaceStore store,
            CreateNodeOperation createNodeOperation,
            RenameNodeOperation renameNodeOperation,
            DeleteNodesOperation deleteNodesOperation,
            TransferOperation transferOperation,
            AddBookmarkOperation addBookmarkOperation,
            RemoveBookmarksOperation removeBookmarksOperation,
            RestoreTrashEntriesOperation restoreTrashEntriesOperation,
            EmptyTrashOperation emptyTrashOperation) {
        this(store, createNodeOperation, renameNodeOperation, deleteNodesOperation,
                transferOperation, addBookmarkOperation, removeBookmarksOperation,
                restoreTrashEntriesOperation, emptyTrashOperation, RuleEngine.defaults());
    }

    WorkspaceCommandDispatcher(
            WorkspaceStore store,
            CreateNodeOperation createNodeOperation,
            RenameNodeOperation renameNodeOperation,
            DeleteNodesOperation deleteNodesOperation,
            TransferOperation transferOperation,
            AddBookmarkOperation addBookmarkOperation,
            RemoveBookmarksOperation removeBookmarksOperation,
            RestoreTrashEntriesOperation restoreTrashEntriesOperation,
            EmptyTrashOperation emptyTrashOperation,
            RuleEngine rules) {
        this.store = store;
        this.createNodeOperation = createNodeOperation;
        this.renameNodeOperation = renameNodeOperation;
        this.deleteNodesOperation = deleteNodesOperation;
        this.transferOperation = transferOperation;
        this.addBookmarkOperation = addBookmarkOperation;
        this.removeBookmarksOperation = removeBookmarksOperation;
        this.restoreTrashEntriesOperation = restoreTrashEntriesOperation;
        this.emptyTrashOperation = emptyTrashOperation;
        this.rules = rules;
    }

    @NonNull
    public EnumSet<WorkspaceAction> disabledActions(@NonNull WorkspaceRuleState state) {
        return rules.disabledActions(state);
    }

    @NonNull
    public CreateNodeOperation.Result create(@NonNull CreateNodeOperation.Input input,
                                             @NonNull WorkspaceRuleState state)
            throws NodeException {
        requireAllowed(WorkspaceAction.CREATE, state);
        try {
            CreateNodeOperation.Result result = createNodeOperation.execute(input);
            store.publish(result.mutation);
            return result;
        } catch (NodeException e) {
            if (input.policy == ExistingNamePolicy.REPLACE) {
                store.publish(MutationResult.allLiveSnapshots());
            }
            throw e;
        }
    }

    @NonNull
    public RenameNodeOperation.Result rename(@NonNull RenameNodeOperation.Input input,
                                             @NonNull WorkspaceRuleState state)
            throws NodeException {
        requireAllowed(WorkspaceAction.RENAME, state);
        RenameNodeOperation.Result result = renameNodeOperation.execute(input);
        store.publish(result.mutation);
        return result;
    }

    @NonNull
    public DeleteNodesOperation.Result delete(@NonNull DeleteNodesOperation.Input input,
                                               @NonNull WorkspaceRuleState state)
            throws NodeException {
        requireAllowed(WorkspaceAction.DELETE, state);
        try {
            DeleteNodesOperation.Result result = deleteNodesOperation.execute(input);
            store.publish(result.mutation);
            return result;
        } catch (NodeException e) {
            store.publish(MutationResult.allLiveSnapshots());
            throw e;
        }
    }

    @NonNull
    public TransferOperation.Result transfer(@NonNull TransferOperation.Input input,
                                             @NonNull WorkspaceRuleState state)
            throws NodeException {
        WorkspaceAction action = input.kind == TransferKind.COPY
                ? WorkspaceAction.COPY : WorkspaceAction.MOVE;
        requireAllowed(action, state);
        try {
            TransferOperation.Result result = transferOperation.execute(input);
            store.publish(result.mutation);
            return result;
        } catch (RuntimeException e) {
            store.publish(MutationResult.allLiveSnapshots());
            throw e;
        }
    }

    @NonNull
    public AddBookmarkOperation.Result addBookmark(@NonNull VirtualNode node,
                                                    @NonNull WorkspaceRuleState state)
            throws NodeException {
        requireAllowed(WorkspaceAction.BOOKMARK, state);
        AddBookmarkOperation.Result result = addBookmarkOperation.execute(node);
        store.publish(result.mutation);
        return result;
    }

    @NonNull
    public RemoveBookmarksOperation.Result removeBookmarks(@NonNull List<NodePath> paths) {
        RemoveBookmarksOperation.Result result = removeBookmarksOperation.execute(paths);
        store.publish(result.mutation);
        return result;
    }

    @NonNull
    public RestoreTrashEntriesOperation.Result restoreTrashEntries(@NonNull List<String> entryIds) {
        RestoreTrashEntriesOperation.Result result = restoreTrashEntriesOperation.execute(entryIds);
        store.publish(result.mutation);
        return result;
    }

    public void emptyTrash() throws NodeException {
        EmptyTrashOperation.Result result = emptyTrashOperation.execute();
        store.publish(result.mutation);
    }

    private void requireAllowed(@NonNull WorkspaceAction action,
                                @NonNull WorkspaceRuleState state) throws NodeException {
        if (disabledActions(state).contains(action)) {
            throw new NodeException("Action disabled by workspace rule: " + action);
        }
    }
}

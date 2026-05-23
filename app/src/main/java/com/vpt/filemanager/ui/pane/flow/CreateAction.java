package com.vpt.filemanager.ui.pane.flow;

import androidx.annotation.NonNull;

import java.util.Collections;

import com.vpt.filemanager.ui.pane.DualPaneHostFragment;
import com.vpt.filemanager.ui.dialog.ConflictDialog;
import com.vpt.filemanager.ui.dialog.CreateItemDialog;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.conflict.NameConflictException;
import com.vpt.filemanager.operations.create.CreateNodeOperation;
import com.vpt.filemanager.operations.create.CreateNodeType;
import com.vpt.filemanager.operations.create.ExistingNamePolicy;
import com.vpt.filemanager.rules.WorkspaceRuleState;
import com.vpt.filemanager.threading.AppExecutors;
import com.vpt.filemanager.workspace.WorkspaceCommandDispatcher;

/**
 * Android boundary for the bottom-bar create button.
 *
 * <p>This class only collects UI input, bridges conflict choice dialogs, and posts user-visible
 * effects. {@link WorkspaceCommandDispatcher} enforces rules and publishes operation mutations.
 */
public final class CreateAction {
    private final DualPaneHostFragment host;
    private final AppExecutors executors;
    private final NodeFactory nodeFactory;
    private final WorkspaceCommandDispatcher commands;

    public CreateAction(DualPaneHostFragment host,
                        AppExecutors executors,
                        NodeFactory nodeFactory,
                        WorkspaceCommandDispatcher commands) {
        this.host = host;
        this.executors = executors;
        this.nodeFactory = nodeFactory;
        this.commands = commands;
    }

    public void execute() {
        CreateItemDialog.show(host.requireContext(), this::attemptCreate);
    }

    private void attemptCreate(boolean isFolder, String name) {
        NodePath parentPath = host.activeVm().currentPath();
        if (parentPath == null) {
            return;
        }
        CreateNodeType type = isFolder ? CreateNodeType.FOLDER : CreateNodeType.FILE;
        runCreate(parentPath, type, name, ExistingNamePolicy.FAIL);
    }

    private void runCreate(@NonNull NodePath parentPath,
                           @NonNull CreateNodeType type,
                           @NonNull String name,
                           @NonNull ExistingNamePolicy policy) {
        NodePath inactivePath = host.inactiveVm().currentPath();
        executors.io().submit(() -> {
            try {
                VirtualNode parent = nodeFactory.fromPath(parentPath);
                commands.create(new CreateNodeOperation.Input(parent, type, name, policy),
                        WorkspaceRuleState.of(Collections.emptySet(), null, parentPath,
                                inactivePath));
                postToast(type == CreateNodeType.FOLDER ? "Folder created" : "File created");
            } catch (NameConflictException conflict) {
                showConflict(parentPath, type, conflict.name());
            } catch (NodeException e) {
                postToast(e.getMessage() == null ? "Create failed" : e.getMessage());
            }
        });
    }

    private void showConflict(@NonNull NodePath parentPath,
                              @NonNull CreateNodeType type,
                              @NonNull String name) {
        executors.main().execute(() -> {
            if (!host.isAdded() || host.getActivity() == null) {
                return;
            }
            ConflictDialog.show(host.requireContext(), name, new ConflictDialog.OnChoice() {
                @Override
                public void onReplace() {
                    runCreate(parentPath, type, name, ExistingNamePolicy.REPLACE);
                }

                @Override
                public void onKeepBoth() {
                    runCreate(parentPath, type, name, ExistingNamePolicy.KEEP_BOTH);
                }
            });
        });
    }

    private void postToast(@NonNull String message) {
        executors.main().execute(() -> {
            if (host.isAdded() && host.getActivity() != null) {
                host.toast(message);
            }
        });
    }
}

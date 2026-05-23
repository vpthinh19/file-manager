package com.vpt.filemanager.ui.pane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import com.vpt.filemanager.threading.AppExecutors;
import com.vpt.filemanager.data.prefs.UserPreferences;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.operations.sort.SortOrder;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.delete.DeleteNodesOperation;
import com.vpt.filemanager.operations.rename.RenameNodeOperation;
import com.vpt.filemanager.event.LiveEvent;
import com.vpt.filemanager.operations.navigation.NavigateToParentOperation;
import com.vpt.filemanager.operations.selection.SelectRangeOperation;
import com.vpt.filemanager.operations.sort.SortNodesOperation;
import com.vpt.filemanager.workspace.DirectorySnapshot;
import com.vpt.filemanager.workspace.MutationResult;
import com.vpt.filemanager.workspace.WorkspaceCommandDispatcher;
import com.vpt.filemanager.workspace.WorkspaceStore;
import com.vpt.filemanager.rules.WorkspaceRuleState;

/**
 * Browser pane state machine: current virtual location, materialized listing, selection, and
 * back/forward stacks.
 *
 * <p>It resolves visible nodes and asks {@link WorkspaceStore} to reconcile snapshots. Mutating
 * intent is submitted through {@link WorkspaceCommandDispatcher}; this view model does not
 * publish virtual-tree mutation scopes itself.
 *
 * <p>Process-death restore: {@link SavedStateHandle} lưu currentPath string. Restore kick off
 * load() ngay trong constructor để Fragment không phải re-init.
 */
@HiltViewModel
public final class PaneViewModel extends ViewModel {
    private static final String KEY_PATH = "current_path";

    private final SavedStateHandle savedState;
    private final NodeFactory nodeFactory;
    private final WorkspaceCommandDispatcher commands;
    private final AppExecutors executors;
    private final UserPreferences prefs;
    private final WorkspaceStore workspace;
    private final NavigateToParentOperation navigateToParentOperation = new NavigateToParentOperation();
    private final SelectRangeOperation selectRangeOperation = new SelectRangeOperation();
    private final SortNodesOperation sortNodesOperation = new SortNodesOperation();

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(new UiState.Loading());
    private final MutableLiveData<Set<NodePath>> selection = new MutableLiveData<>(Collections.emptySet());
    /**
     * Selection-mode flag, OBSERVABLE separately từ selection set. Phase R-7a: split để
     * "Deselect all" button có thể clear items mà KHÔNG exit mode (X button mới exit).
     *
     * <p>Trước R-7a: {@code isInSelectionMode = !selection.isEmpty()} → derived. Sau: explicit flag.
     */
    private final MutableLiveData<Boolean> selectionMode = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canGoBack = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canGoForward = new MutableLiveData<>(false);
    private final LiveEvent<String> events = new LiveEvent<>();
    private final ArrayDeque<NodePath> backStack = new ArrayDeque<>();
    private final ArrayDeque<NodePath> forwardStack = new ArrayDeque<>();
    private NodePath currentPath;
    private List<VirtualNode> lastVisibleNodes = Collections.emptyList();
    private Future<?> pendingLoad;

    @Inject
    public PaneViewModel(
            SavedStateHandle savedState,
            NodeFactory nodeFactory,
            WorkspaceCommandDispatcher commands,
            AppExecutors executors,
            UserPreferences prefs,
            WorkspaceStore workspace) {
        this.savedState = savedState;
        this.nodeFactory = nodeFactory;
        this.commands = commands;
        this.executors = executors;
        this.prefs = prefs;
        this.workspace = workspace;
        restoreSavedPath();
    }

    /**
     * Re-hydrate pane's last location sau process death (MT Manager parity). Kick off load too
     * — otherwise Fragment thấy currentPath non-null nên skip initial navigation → uiState stuck
     * Loading. Corrupt path swallow silently; Fragment navigates về root.
     */
    private void restoreSavedPath() {
        String saved = savedState.get(KEY_PATH);
        if (saved == null) {
            return;
        }
        try {
            currentPath = NodePath.parse(saved);
            workspace.retain(currentPath);
            load(currentPath, false);
        } catch (IllegalArgumentException ignored) {
            savedState.remove(KEY_PATH);
        }
    }

    public LiveData<UiState> uiState() {
        return uiState;
    }

    public LiveData<Set<NodePath>> selection() {
        return selection;
    }

    public LiveData<Boolean> selectionMode() {
        return selectionMode;
    }

    public LiveData<String> events() {
        return events;
    }

    public LiveData<Boolean> canGoBack() {
        return canGoBack;
    }

    public LiveData<Boolean> canGoForward() {
        return canGoForward;
    }

    public NodePath currentPath() {
        return currentPath;
    }

    public boolean isInSelectionMode() {
        return Boolean.TRUE.equals(selectionMode.getValue());
    }

    /**
     * Lookup node hiện đang hiển thị theo path. Dùng bởi SelectionBarController khi tính disable
     * rules cho bottom sheet (cần biết single selection là file hay folder).
     */
    @Nullable
    public VirtualNode findNode(@Nullable NodePath path) {
        if (path == null) {
            return null;
        }
        for (VirtualNode node : lastVisibleNodes) {
            if (path.equals(node.path())) {
                return node;
            }
        }
        return null;
    }

    /**
     * Toggle 1 node trong selection set. No-op khi không trong mode — caller phải gọi
     * {@link #enterSelectionAndToggle(VirtualNode)} từ long-press entry point trước.
     */
    public void toggleSelect(VirtualNode node) {
        if (node.isParent() || !isInSelectionMode()) {
            return;
        }
        Set<NodePath> current = selection.getValue();
        LinkedHashSet<NodePath> next = current == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(current);
        if (!next.add(node.path())) {
            next.remove(node.path());
        }
        selection.setValue(Collections.unmodifiableSet(next));
    }

    /**
     * Entry point khi long-press: bật mode + add node. Nếu đã trong mode, hành xử như
     * toggleSelect (cho phép long-press toggle 1 item bất kỳ thời điểm nào).
     */
    public void enterSelectionAndToggle(VirtualNode node) {
        if (node.isParent()) {
            return;
        }
        if (!isInSelectionMode()) {
            selectionMode.setValue(true);
        }
        Set<NodePath> current = selection.getValue();
        LinkedHashSet<NodePath> next = current == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(current);
        if (!next.add(node.path())) {
            next.remove(node.path());
        }
        selection.setValue(Collections.unmodifiableSet(next));
    }

    public void selectAllVisible() {
        if (lastVisibleNodes.isEmpty()) {
            return;
        }
        LinkedHashSet<NodePath> next = new LinkedHashSet<>();
        for (VirtualNode node : lastVisibleNodes) {
            if (!node.isParent()) {
                next.add(node.path());
            }
        }
        if (!isInSelectionMode()) {
            selectionMode.setValue(true);
        }
        selection.setValue(Collections.unmodifiableSet(next));
    }

    /**
     * "Deselect all" button: clear items, GIỮ mode active (user vẫn ở selection bar, có thể
     * tap items để select lại). Khác với {@link #exitSelectionMode()} = X button = thoát hẳn.
     */
    public void clearSelection() {
        Set<NodePath> current = selection.getValue();
        if (current == null || current.isEmpty()) {
            return;
        }
        selection.setValue(Collections.emptySet());
    }

    /** X button: clear items + tắt mode. Cũng gọi tự động khi switchPath (navigate đổi folder). */
    public void exitSelectionMode() {
        if (selection.getValue() != null && !selection.getValue().isEmpty()) {
            selection.setValue(Collections.emptySet());
        }
        if (isInSelectionMode()) {
            selectionMode.setValue(false);
        }
    }

    /**
     * Select range button: chọn TẤT CẢ items giữa min/max index của các items đang selected.
     * Yêu cầu ít nhất 2 items selected. No-op nếu &lt; 2.
     *
     * <p>Edge cases:
     * <ul>
     *   <li>0/1 selected → no-op (button disabled ở UI, đây là defensive)</li>
     *   <li>2 selected contiguous → already a "range" → no-op effective (set unchanged)</li>
     *   <li>2+ selected scattered → fill gap (convex hull theo index)</li>
     *   <li>Parent ".." marker — không có trong {@link #lastVisibleNodes} (chỉ adapter-side),
     *       safe không skip</li>
     * </ul>
     */
    public void selectRange() {
        Set<NodePath> current = selection.getValue();
        if (current == null || current.size() < 2 || lastVisibleNodes.isEmpty()) {
            return;
        }
        SelectRangeOperation.Output output = selectRangeOperation.execute(
                new SelectRangeOperation.Input(current, lastVisibleNodes));
        selection.setValue(Collections.unmodifiableSet(new LinkedHashSet<>(output.selection)));
    }

    public void navigateTo(NodePath path) {
        if (currentPath != null && !currentPath.equals(path)) {
            backStack.push(currentPath);
            forwardStack.clear();
            emitStackState();
        }
        switchPath(path);
    }

    public boolean back() {
        if (backStack.isEmpty()) {
            return false;
        }
        if (currentPath != null) {
            forwardStack.push(currentPath);
        }
        NodePath prev = backStack.pop();
        emitStackState();
        switchPath(prev);
        return true;
    }

    public boolean forward() {
        if (forwardStack.isEmpty()) {
            return false;
        }
        if (currentPath != null) {
            backStack.push(currentPath);
        }
        NodePath next = forwardStack.pop();
        emitStackState();
        switchPath(next);
        return true;
    }

    private void switchPath(NodePath path) {
        // Navigate đổi folder → selection của folder cũ không còn hợp lý → exit mode hẳn.
        exitSelectionMode();
        NodePath previous = currentPath;
        if (!path.equals(previous)) {
            workspace.retain(path);
            if (previous != null) {
                workspace.release(previous);
            }
        }
        currentPath = path;
        savedState.set(KEY_PATH, path.toString());
        load(path, false);
    }

    private void emitStackState() {
        canGoBack.setValue(!backStack.isEmpty());
        canGoForward.setValue(!forwardStack.isEmpty());
    }

    public void openArchive(NodePath archiveFile) {
        navigateTo(NodePath.inArchive(archiveFile, "/"));
    }

    public boolean navigateUp() {
        if (currentPath == null) {
            return false;
        }
        NavigateToParentOperation.Output output = navigateToParentOperation.execute(
                new NavigateToParentOperation.Input(currentPath));
        if (output.parentPath == null) {
            return false;
        }
        navigateTo(output.parentPath);
        return true;
    }

    public void refresh() {
        if (currentPath != null) {
            load(currentPath, false);
        }
    }

    public void reconcile(@NonNull MutationResult mutation) {
        if (currentPath != null && mutation.affectsListing(currentPath)) {
            load(currentPath, true);
        }
    }

    /**
     * Apply sort order. KHÔNG re-list từ disk — sort {@code lastVisibleNodes} snapshot in-memory
     * + re-emit Content. Sort instant bất kể folder size, không tốn IO. Persist global qua UserPreferences.
     */
    public void setSort(@NonNull SortOrder order) {
        prefs.setSortOrder(order);
        if (lastVisibleNodes.isEmpty() || currentPath == null) {
            return;
        }
        List<VirtualNode> sorted = sortNodesOperation.execute(
                new SortNodesOperation.Input(lastVisibleNodes, order)).nodes;
        lastVisibleNodes = sorted;
        uiState.setValue(new UiState.Content(currentPath, sorted));
    }

    @NonNull
    public SortOrder sortOrder() {
        return prefs.sortOrder();
    }

    public void rename(NodePath path, String newName, @NonNull WorkspaceRuleState ruleState) {
        if (path == null || newName == null || newName.isBlank()) {
            return;
        }
        String trimmed = newName.trim();
        // Exit mode trước submit — single-target action xong, selected NodePath cũng sắp
        // disappear (replaced bởi name mới); DiffUtil treat = remove+insert.
        exitSelectionMode();
        runAction(() -> {
            VirtualNode node = nodeFactory.fromPath(path);
            return commands.rename(new RenameNodeOperation.Input(node, trimmed), ruleState);
        }, "Renamed");
    }

    public void delete(VirtualNode node, @NonNull WorkspaceRuleState ruleState) {
        if (node == null) {
            return;
        }
        runAction(() -> commands.delete(
                new DeleteNodesOperation.Input(List.of(node), false), ruleState), "Moved to trash");
    }

    public void deleteSelected(@NonNull WorkspaceRuleState ruleState) {
        Set<NodePath> current = selection.getValue();
        if (current == null || current.isEmpty()) {
            return;
        }
        List<NodePath> snapshot = List.copyOf(current);
        // Destructive batch xong → items gone → exit mode hẳn (không thể act tiếp).
        exitSelectionMode();
        runAction(() -> {
            List<VirtualNode> nodes = new ArrayList<>(snapshot.size());
            for (NodePath p : snapshot) {
                nodes.add(nodeFactory.fromPath(p));
            }
            return commands.delete(new DeleteNodesOperation.Input(nodes, true), ruleState);
        }, null);
    }

    public void restoreSelected() {
        Set<NodePath> current = selection.getValue();
        if (current == null || current.isEmpty()) {
            return;
        }
        List<NodePath> snapshot = List.copyOf(current);
        exitSelectionMode();
        executors.io().submit(() -> {
            List<String> entryIds = new ArrayList<>(snapshot.size());
            for (NodePath p : snapshot) {
                entryIds.add(p.authority());
            }
            events.postValue(commands.restoreTrashEntries(entryIds).batch.message("restored"));
        });
    }

    public void emptyTrash() {
        runAction(() -> { commands.emptyTrash(); return null; }, "Trash emptied");
    }

    public void removeBookmarksSelected() {
        Set<NodePath> current = selection.getValue();
        if (current == null || current.isEmpty()) {
            return;
        }
        List<NodePath> snapshot = List.copyOf(current);
        exitSelectionMode();
        executors.io().submit(() -> {
            events.postValue(commands.removeBookmarks(snapshot).batch.message("bookmark removed"));
        });
    }

    public void addBookmarkSelected(@NonNull WorkspaceRuleState ruleState) {
        Set<NodePath> current = selection.getValue();
        if (current == null || current.size() != 1) {
            return;
        }
        NodePath path = current.iterator().next();
        exitSelectionMode();
        executors.io().submit(() -> {
            try {
                VirtualNode node = nodeFactory.fromPath(path);
                commands.addBookmark(node, ruleState);
                events.postValue("Bookmarked");
            } catch (Throwable e) {
                events.postValue("Bookmark failed: "
                        + (e.getMessage() == null ? "unknown" : e.getMessage()));
            }
        });
    }

    /**
     * Load directory listing on io(). Cancel any prior in-flight load để rapid taps không queue
     * stale Content emissions. Post-load state check guards race: slow load finish SAU khi user
     * đã navigate elsewhere → only emit nếu path vừa load === currentPath.
     */
    private void load(NodePath path, boolean reconcileAfterMutation) {
        if (pendingLoad != null) {
            pendingLoad.cancel(true);
            pendingLoad = null;
        }
        uiState.postValue(new UiState.Loading());
        pendingLoad = executors.io().submit(() -> {
            try {
                DirectorySnapshot snapshot = reconcileAfterMutation
                        ? workspace.reconcile(path)
                        : workspace.reload(path);
                List<VirtualNode> nodes = snapshot.children;
                if (!path.equals(currentPath)) {
                    return;
                }
                if (nodes == null || nodes.isEmpty()) {
                    lastVisibleNodes = Collections.emptyList();
                    uiState.postValue(new UiState.Empty(path));
                } else {
                    List<VirtualNode> sorted = sortNodesOperation.execute(
                            new SortNodesOperation.Input(nodes, prefs.sortOrder())).nodes;
                    lastVisibleNodes = sorted;
                    uiState.postValue(new UiState.Content(path, sorted));
                }
            } catch (Throwable e) {
                if (!path.equals(currentPath)) {
                    return;
                }
                lastVisibleNodes = Collections.emptyList();
                uiState.postValue(new UiState.Error(path,
                        e.getMessage() == null ? "Unknown error" : e.getMessage()));
            }
        });
    }

    /** Runs a workspace command on IO; the dispatcher publishes its mutation output. */
    private void runAction(ThrowingAction action, String successMessage) {
        executors.io().submit(() -> {
            try {
                Object result = action.run();
                String message = successMessage;
                if (message == null && result instanceof DeleteNodesOperation.Result deleteResult) {
                    message = deleteResult.message("moved to trash");
                }
                if (message != null) {
                    events.postValue(message);
                }
            } catch (Throwable e) {
                events.postValue(e.getMessage() == null ? "Action failed" : e.getMessage());
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingAction {
        Object run() throws Throwable;
    }

    @Override
    protected void onCleared() {
        if (pendingLoad != null) {
            pendingLoad.cancel(true);
        }
        if (currentPath != null) {
            workspace.release(currentPath);
        }
    }

    public abstract static class UiState {
        public static final class Loading extends UiState {
        }

        public static final class Content extends UiState {
            public final NodePath path;
            public final List<VirtualNode> nodes;
            public final int folderCount;
            public final int fileCount;
            public final long freeBytes;
            public final long totalBytes;

            public Content(NodePath path, List<VirtualNode> nodes) {
                this.path = path;
                this.nodes = List.copyOf(nodes);
                int folders = 0;
                int files = 0;
                for (VirtualNode node : nodes) {
                    if (node.isFolder()) {
                        folders++;
                    } else {
                        files++;
                    }
                }
                this.folderCount = folders;
                this.fileCount = files;
                if (path.isLocal()) {
                    java.io.File file = new java.io.File(path.path());
                    long total = 0;
                    long free = 0;
                    try {
                        total = file.getTotalSpace();
                        free = file.getUsableSpace();
                    } catch (SecurityException ignored) {
                    }
                    this.totalBytes = total;
                    this.freeBytes = free;
                } else {
                    this.totalBytes = -1;
                    this.freeBytes = -1;
                }
            }
        }

        public static final class Empty extends UiState {
            public final NodePath path;

            public Empty(NodePath path) {
                this.path = path;
            }
        }

        public static final class Error extends UiState {
            public final NodePath path;
            public final String message;

            public Error(NodePath path, String message) {
                this.path = path;
                this.message = message;
            }
        }
    }
}

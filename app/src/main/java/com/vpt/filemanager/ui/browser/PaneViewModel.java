package com.vpt.filemanager.ui.browser;

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
import com.vpt.filemanager.core.AppExecutors;
import com.vpt.filemanager.core.Prefs;
import com.vpt.filemanager.core.StorageScope;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.SortOrder;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.FileOps;
import com.vpt.filemanager.operations.TrashOps;
import com.vpt.filemanager.ui.LiveEvent;

/**
 * Browser pane state machine — holds current location, listing, selection, back/forward stacks.
 *
 * <p>Phase R-5b: migrated từ {@code FileNode} + {@code listDirectoryUseCase} + 4 other use cases
 * sang DOM ảo concept: {@link NodeFactory} resolve paths, {@link VirtualNode#children()} list
 * directly, {@link FileOps} + {@link TrashOps} cho CRUD. Use case layer xóa.
 *
 * <p>Backbone API contract giữ nguyên cho controllers/actions: {@code navigateTo(FilePath)},
 * {@code rename(FilePath, String)}, {@code createFolder(String)}, etc. — chỉ implementation thay
 * đổi. Selection vẫn {@code Set<FilePath>} (identity stays).
 *
 * <p>Process-death restore: {@link SavedStateHandle} lưu currentPath string. Restore kick off
 * load() ngay trong constructor để Fragment không phải re-init.
 */
@HiltViewModel
public final class PaneViewModel extends ViewModel {
    private static final String KEY_PATH = "current_path";

    private final SavedStateHandle savedState;
    private final NodeFactory nodeFactory;
    private final FileOps fileOps;
    private final TrashOps trashOps;
    private final AppExecutors executors;
    private final Prefs prefs;

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(new UiState.Loading());
    private final MutableLiveData<Set<FilePath>> selection = new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<Boolean> canGoBack = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canGoForward = new MutableLiveData<>(false);
    private final LiveEvent<String> events = new LiveEvent<>();
    private final ArrayDeque<FilePath> backStack = new ArrayDeque<>();
    private final ArrayDeque<FilePath> forwardStack = new ArrayDeque<>();
    private FilePath currentPath;
    private List<VirtualNode> lastVisibleNodes = Collections.emptyList();
    private Future<?> pendingLoad;

    @Inject
    public PaneViewModel(
            SavedStateHandle savedState,
            NodeFactory nodeFactory,
            FileOps fileOps,
            TrashOps trashOps,
            AppExecutors executors,
            Prefs prefs) {
        this.savedState = savedState;
        this.nodeFactory = nodeFactory;
        this.fileOps = fileOps;
        this.trashOps = trashOps;
        this.executors = executors;
        this.prefs = prefs;
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
            currentPath = FilePath.parse(saved);
            load(currentPath);
        } catch (IllegalArgumentException ignored) {
            savedState.remove(KEY_PATH);
        }
    }

    public LiveData<UiState> uiState() {
        return uiState;
    }

    public LiveData<Set<FilePath>> selection() {
        return selection;
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

    public FilePath currentPath() {
        return currentPath;
    }

    public boolean isInSelectionMode() {
        Set<FilePath> current = selection.getValue();
        return current != null && !current.isEmpty();
    }

    /**
     * Lookup node hiện đang hiển thị theo path. Dùng bởi SelectionBarController khi tính disable
     * rules cho bottom sheet (cần biết single selection là file hay folder).
     */
    @Nullable
    public VirtualNode findNode(@Nullable FilePath path) {
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

    public void toggleSelect(VirtualNode node) {
        if (node.isParent()) {
            return;
        }
        Set<FilePath> current = selection.getValue();
        LinkedHashSet<FilePath> next = current == null
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
        LinkedHashSet<FilePath> next = new LinkedHashSet<>();
        for (VirtualNode node : lastVisibleNodes) {
            if (!node.isParent()) {
                next.add(node.path());
            }
        }
        selection.setValue(Collections.unmodifiableSet(next));
    }

    public void clearSelection() {
        if (!isInSelectionMode()) {
            return;
        }
        selection.setValue(Collections.emptySet());
    }

    public void navigateTo(FilePath path) {
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
        FilePath prev = backStack.pop();
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
        FilePath next = forwardStack.pop();
        emitStackState();
        switchPath(next);
        return true;
    }

    private void switchPath(FilePath path) {
        clearSelection();
        currentPath = path;
        savedState.set(KEY_PATH, path.toString());
        load(path);
    }

    private void emitStackState() {
        canGoBack.setValue(!backStack.isEmpty());
        canGoForward.setValue(!forwardStack.isEmpty());
    }

    public void openArchive(FilePath archiveFile) {
        navigateTo(FilePath.inArchive(archiveFile, "/"));
    }

    public boolean navigateUp() {
        if (currentPath == null) {
            return false;
        }
        if (StorageScope.isAtRoot(currentPath)) {
            return false;
        }
        if (currentPath.isArchive() && "/".equals(currentPath.path())) {
            FilePath archiveFile = FilePath.parse(currentPath.authority());
            navigateTo(archiveFile.parent());
            return true;
        }
        navigateTo(currentPath.parent());
        return true;
    }

    public void refresh() {
        if (currentPath != null) {
            load(currentPath);
        }
    }

    /**
     * Apply sort order. KHÔNG re-list từ disk — sort {@code lastVisibleNodes} snapshot in-memory
     * + re-emit Content. Sort instant bất kể folder size, không tốn IO. Persist global qua Prefs.
     */
    public void setSort(@NonNull SortOrder order) {
        prefs.setSortOrder(order);
        if (lastVisibleNodes.isEmpty() || currentPath == null) {
            return;
        }
        List<VirtualNode> sorted = new ArrayList<>(lastVisibleNodes);
        sorted.sort(order.folderFirstComparator());
        lastVisibleNodes = sorted;
        uiState.setValue(new UiState.Content(currentPath, sorted));
    }

    @NonNull
    public SortOrder sortOrder() {
        return prefs.sortOrder();
    }

    public void createFolder(String name) {
        if (!isWritableContext() || name == null || name.isBlank()) {
            return;
        }
        String trimmed = name.trim();
        runActionAndRefresh(() -> {
            VirtualNode parent = nodeFactory.fromPath(currentPath);
            return fileOps.createFolder(parent, trimmed);
        }, "Folder created");
    }

    public void createFile(String name) {
        if (!isWritableContext() || name == null || name.isBlank()) {
            return;
        }
        String trimmed = name.trim();
        runActionAndRefresh(() -> {
            VirtualNode parent = nodeFactory.fromPath(currentPath);
            return fileOps.createFile(parent, trimmed);
        }, "File created");
    }

    public void rename(FilePath path, String newName) {
        if (path == null || newName == null || newName.isBlank()) {
            return;
        }
        String trimmed = newName.trim();
        // Drop selection trước submit — selected FilePath sắp disappear (replaced bởi name mới),
        // DiffUtil treat = remove+insert. Không clear thì UI giữ "1 selected" trên row đã chết.
        clearSelection();
        runActionAndRefresh(() -> {
            VirtualNode node = nodeFactory.fromPath(path);
            return fileOps.rename(node, trimmed);
        }, "Renamed");
    }

    /**
     * Phase 2C-6 create-conflict "Replace": move existing entry vào Trash (recoverable) → tạo
     * entry mới. Sequential single io task — delete fail → create never runs.
     */
    public void deleteThenCreate(FilePath path, boolean isFolder) {
        if (path == null) {
            return;
        }
        executors.io().submit(() -> {
            try {
                VirtualNode existing = nodeFactory.fromPath(path);
                trashOps.moveToTrash(existing);
                VirtualNode parent = nodeFactory.fromPath(path.parent());
                if (isFolder) {
                    fileOps.createFolder(parent, path.name());
                } else {
                    fileOps.createFile(parent, path.name());
                }
                events.postValue(isFolder ? "Folder replaced" : "File replaced");
                refresh();
            } catch (Throwable e) {
                events.postValue("Replace failed: "
                        + (e.getMessage() == null ? "unknown" : e.getMessage()));
            }
        });
    }

    public void delete(VirtualNode node) {
        if (node == null) {
            return;
        }
        runActionAndRefresh(() -> { trashOps.moveToTrash(node); return null; },
                "Moved to trash");
    }

    public void deleteSelected() {
        Set<FilePath> current = selection.getValue();
        if (current == null || current.isEmpty()) {
            return;
        }
        List<FilePath> snapshot = List.copyOf(current);
        clearSelection();
        runActionAndRefresh(() -> {
            for (FilePath p : snapshot) {
                VirtualNode n = nodeFactory.fromPath(p);
                trashOps.moveToTrash(n);
            }
            return null;
        }, snapshot.size() + " moved to trash");
    }

    private boolean isWritableContext() {
        return currentPath != null && currentPath.isLocal();
    }

    /**
     * Load directory listing on io(). Cancel any prior in-flight load để rapid taps không queue
     * stale Content emissions. Post-load state check guards race: slow load finish SAU khi user
     * đã navigate elsewhere → only emit nếu path vừa load === currentPath.
     */
    private void load(FilePath path) {
        if (pendingLoad != null) {
            pendingLoad.cancel(true);
            pendingLoad = null;
        }
        uiState.postValue(new UiState.Loading());
        pendingLoad = executors.io().submit(() -> {
            try {
                VirtualNode currentNode = nodeFactory.fromPath(path);
                List<VirtualNode> nodes = currentNode.children();
                if (!path.equals(currentPath)) {
                    return;
                }
                if (nodes == null || nodes.isEmpty()) {
                    lastVisibleNodes = Collections.emptyList();
                    uiState.postValue(new UiState.Empty(path));
                } else {
                    List<VirtualNode> sorted = new ArrayList<>(nodes);
                    sorted.sort(prefs.sortOrder().folderFirstComparator());
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

    private void runActionAndRefresh(ThrowingAction action, String successMessage) {
        executors.io().submit(() -> {
            try {
                action.run();
                events.postValue(successMessage);
                refresh();
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
    }

    public abstract static class UiState {
        public static final class Loading extends UiState {
        }

        public static final class Content extends UiState {
            public final FilePath path;
            public final List<VirtualNode> nodes;
            public final int folderCount;
            public final int fileCount;
            public final long freeBytes;
            public final long totalBytes;

            public Content(FilePath path, List<VirtualNode> nodes) {
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
            public final FilePath path;

            public Empty(FilePath path) {
                this.path = path;
            }
        }

        public static final class Roots extends UiState {
            public final FilePath path;
            public final List<VirtualNode> roots;

            public Roots(FilePath path, List<VirtualNode> roots) {
                this.path = path;
                this.roots = List.copyOf(roots);
            }
        }

        public static final class Error extends UiState {
            public final FilePath path;
            public final String message;

            public Error(FilePath path, String message) {
                this.path = path;
                this.message = message;
            }
        }
    }
}

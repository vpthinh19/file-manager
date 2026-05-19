package com.vpt.filemanager.ui.browser;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import com.vpt.filemanager.core.AppExecutors;
import com.vpt.filemanager.core.StorageScope;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.usecase.CreateFileUseCase;
import com.vpt.filemanager.domain.usecase.CreateFolderUseCase;
import com.vpt.filemanager.domain.usecase.DeleteFilesUseCase;
import com.vpt.filemanager.domain.usecase.ListDirectoryUseCase;
import com.vpt.filemanager.domain.usecase.RenameFileUseCase;
import com.vpt.filemanager.ui.LiveEvent;

/**
 * Browser pane state machine: holds the current location, the directory listing, the selection,
 * and the back/forward stacks. Use cases are synchronous + throwing (R3 flattening); this VM owns
 * the io scheduling so use cases stay thread-agnostic and easy to unit test.
 *
 * <p>The constructor restores any prior location from {@link SavedStateHandle} so process death
 * lands the user back where they were (MT-Manager parity).
 */
@HiltViewModel
public final class PaneViewModel extends ViewModel {
    private static final String KEY_PATH = "current_path";

    private final SavedStateHandle savedState;
    private final ListDirectoryUseCase listDirectoryUseCase;
    private final CreateFileUseCase createFileUseCase;
    private final CreateFolderUseCase createFolderUseCase;
    private final DeleteFilesUseCase deleteFilesUseCase;
    private final RenameFileUseCase renameFileUseCase;
    private final AppExecutors executors;

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(new UiState.Loading());
    private final MutableLiveData<Set<FilePath>> selection = new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<Boolean> canGoBack = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canGoForward = new MutableLiveData<>(false);
    private final LiveEvent<String> events = new LiveEvent<>();
    private final ArrayDeque<FilePath> backStack = new ArrayDeque<>();
    private final ArrayDeque<FilePath> forwardStack = new ArrayDeque<>();
    private FilePath currentPath;
    private List<FileNode> lastVisibleNodes = Collections.emptyList();
    private Future<?> pendingLoad;

    @Inject
    public PaneViewModel(
            SavedStateHandle savedState,
            ListDirectoryUseCase listDirectoryUseCase,
            CreateFileUseCase createFileUseCase,
            CreateFolderUseCase createFolderUseCase,
            DeleteFilesUseCase deleteFilesUseCase,
            RenameFileUseCase renameFileUseCase,
            AppExecutors executors) {
        this.savedState = savedState;
        this.listDirectoryUseCase = listDirectoryUseCase;
        this.createFileUseCase = createFileUseCase;
        this.createFolderUseCase = createFolderUseCase;
        this.deleteFilesUseCase = deleteFilesUseCase;
        this.renameFileUseCase = renameFileUseCase;
        this.executors = executors;
        restoreSavedPath();
    }

    /**
     * Re-hydrate the pane's last location after process death so the user returns to exactly where
     * they were (MT Manager parity). We kick off a load here too, otherwise the Fragment would see
     * a non-null currentPath and skip its initial navigation — leaving uiState stuck at Loading.
     * A corrupt/invalid stored path is swallowed silently; the Fragment will then navigate to root.
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
     * Look up the currently-visible node for a path. Used by the host fragment when computing
     * action availability (e.g. is the selected path a folder?) without re-walking the file system.
     */
    @Nullable
    public FileNode findNode(@Nullable FilePath path) {
        if (path == null) {
            return null;
        }
        for (FileNode node : lastVisibleNodes) {
            if (path.equals(node.path())) {
                return node;
            }
        }
        return null;
    }

    public void toggleSelect(FileNode node) {
        if (node instanceof ParentFileNode) {
            return;
        }
        Set<FilePath> current = selection.getValue();
        LinkedHashSet<FilePath> next = current == null ? new LinkedHashSet<>() : new LinkedHashSet<>(current);
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
        for (FileNode node : lastVisibleNodes) {
            if (!(node instanceof ParentFileNode)) {
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

    /**
     * Pop one entry from the back stack. Symmetric with {@link #forward()} — the leaving location
     * is pushed onto the forward stack so the user can ping-pong. Returns {@code false} when no
     * history exists (so the bar button stays disabled).
     */
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

    /** Apply a new currentPath without touching the back/forward stacks (used by back/forward). */
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

    public void createFolder(String name) {
        if (!isWritableContext() || name == null || name.isBlank()) {
            return;
        }
        FilePath target = currentPath.child(name.trim());
        runActionAndRefresh(() -> createFolderUseCase.execute(target), "Folder created");
    }

    public void createFile(String name) {
        if (!isWritableContext() || name == null || name.isBlank()) {
            return;
        }
        FilePath target = currentPath.child(name.trim());
        runActionAndRefresh(() -> createFileUseCase.execute(target), "File created");
    }

    public void rename(FilePath path, String newName) {
        if (path == null || newName == null || newName.isBlank()) {
            return;
        }
        String trimmed = newName.trim();
        // Drop the selection BEFORE submitting — the selected FilePath about to disappear (it'll
        // be replaced by the new name), and DiffUtil treats that as remove+insert. Without this
        // the UI keeps showing "1 selected" with the bottom selection bar visible against a row
        // that no longer exists.
        clearSelection();
        runActionAndRefresh(() -> { renameFileUseCase.execute(path, trimmed); return null; },
                "Renamed");
    }

    /**
     * Replace flow for the Phase 2C-6 create-conflict dialog: delete the colliding entry (to
     * Trash, so the user can recover), then create the new one. Single io task, sequential — if
     * delete fails the create never runs.
     */
    public void deleteThenCreate(FilePath path, boolean isFolder) {
        if (path == null) {
            return;
        }
        executors.io().submit(() -> {
            try {
                deleteFilesUseCase.execute(List.of(path), false);
                if (isFolder) {
                    createFolderUseCase.execute(path);
                } else {
                    createFileUseCase.execute(path);
                }
                events.postValue(isFolder ? "Folder replaced" : "File replaced");
                refresh();
            } catch (Throwable e) {
                events.postValue("Replace failed: "
                        + (e.getMessage() == null ? "unknown" : e.getMessage()));
            }
        });
    }

    public void delete(FileNode node) {
        if (node == null) {
            return;
        }
        FilePath p = node.path();
        runActionAndRefresh(() -> { deleteFilesUseCase.execute(List.of(p), false); return null; },
                "Moved to trash");
    }

    public void deleteSelected() {
        Set<FilePath> current = selection.getValue();
        if (current == null || current.isEmpty()) {
            return;
        }
        List<FilePath> snapshot = List.copyOf(current);
        clearSelection();
        runActionAndRefresh(() -> { deleteFilesUseCase.execute(snapshot, false); return null; },
                snapshot.size() + " moved to trash");
    }

    public void openTrash() {
        if (currentPath == null || !currentPath.isLocal()) {
            return;
        }
        navigateTo(FilePath.local(StorageScope.storageRootFor(currentPath.path()) + "/.AppTrash"));
    }

    private boolean isWritableContext() {
        return currentPath != null && currentPath.isLocal();
    }

    /**
     * Load the directory listing for {@code path} on io(). Cancels any prior in-flight load so a
     * rapid sequence of taps doesn't queue stale Content emissions. The post-load state check
     * guards against a race where a slow load finishes AFTER the user has already navigated
     * elsewhere — we only emit Content/Empty/Error if the path we just loaded is still current.
     */
    private void load(FilePath path) {
        if (pendingLoad != null) {
            pendingLoad.cancel(true);
            pendingLoad = null;
        }
        uiState.postValue(new UiState.Loading());
        pendingLoad = executors.io().submit(() -> {
            try {
                List<FileNode> nodes = listDirectoryUseCase.execute(path);
                if (!path.equals(currentPath)) {
                    return; // user navigated away while we were loading
                }
                if (nodes == null || nodes.isEmpty()) {
                    lastVisibleNodes = Collections.emptyList();
                    uiState.postValue(new UiState.Empty(path));
                } else {
                    lastVisibleNodes = nodes;
                    uiState.postValue(new UiState.Content(path, nodes));
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

    /**
     * Run a CRUD action on io(), then refresh the pane and emit a success / failure event. Caller
     * passes a {@link ThrowingAction} that delegates to a use case — keeps the io scheduling and
     * error fan-out in one place instead of repeated try/catch blocks per action.
     */
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

    /** Functional shim for try/catch composition — use cases throw FileSystemException. */
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
            public final List<FileNode> nodes;
            public final int folderCount;
            public final int fileCount;
            public final long freeBytes;
            public final long totalBytes;

            public Content(FilePath path, List<FileNode> nodes) {
                this.path = path;
                this.nodes = List.copyOf(nodes);
                int folders = 0;
                int files = 0;
                for (FileNode node : nodes) {
                    if (node.isDirectory()) {
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
            public final List<FileNode> roots;

            public Roots(FilePath path, List<FileNode> roots) {
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

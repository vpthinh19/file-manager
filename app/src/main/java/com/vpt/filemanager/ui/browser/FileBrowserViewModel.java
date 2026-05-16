package com.vpt.filemanager.ui.browser;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.concurrent.Future;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.Result;
import com.vpt.filemanager.domain.usecase.CreateFileUseCase;
import com.vpt.filemanager.domain.usecase.CreateFolderUseCase;
import com.vpt.filemanager.domain.usecase.DeleteFilesUseCase;
import com.vpt.filemanager.domain.usecase.ListDirectoryUseCase;
import com.vpt.filemanager.domain.usecase.RenameFileUseCase;
import com.vpt.filemanager.ui.common.LiveEvent;

@HiltViewModel
public final class FileBrowserViewModel extends ViewModel {
    private final ListDirectoryUseCase listDirectoryUseCase;
    private final CreateFileUseCase createFileUseCase;
    private final CreateFolderUseCase createFolderUseCase;
    private final DeleteFilesUseCase deleteFilesUseCase;
    private final RenameFileUseCase renameFileUseCase;
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(new UiState.Loading());
    private final LiveEvent<String> events = new LiveEvent<>();
    private FilePath currentPath;
    private Future<?> pendingLoad;

    @Inject
    public FileBrowserViewModel(
            ListDirectoryUseCase listDirectoryUseCase,
            CreateFileUseCase createFileUseCase,
            CreateFolderUseCase createFolderUseCase,
            DeleteFilesUseCase deleteFilesUseCase,
            RenameFileUseCase renameFileUseCase) {
        this.listDirectoryUseCase = listDirectoryUseCase;
        this.createFileUseCase = createFileUseCase;
        this.createFolderUseCase = createFolderUseCase;
        this.deleteFilesUseCase = deleteFilesUseCase;
        this.renameFileUseCase = renameFileUseCase;
    }

    public LiveData<UiState> uiState() {
        return uiState;
    }

    public LiveData<String> events() {
        return events;
    }

    public FilePath currentPath() {
        return currentPath;
    }

    public void navigateTo(FilePath path) {
        currentPath = path;
        load(path);
    }

    public boolean navigateUp() {
        if (currentPath == null || "/".equals(currentPath.path())) {
            return false;
        }
        if ("/storage/emulated/0".equals(currentPath.path()) || "/sdcard".equals(currentPath.path())) {
            navigateTo(FilePath.local("/"));
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
        if (currentPath == null || name == null || name.isBlank()) {
            return;
        }
        Future<Result<FileNode>> future = createFolderUseCase.execute(currentPath.child(name.trim()));
        waitForFolderCreation(future);
    }

    public void createFile(String name) {
        if (currentPath == null || name == null || name.isBlank()) {
            return;
        }
        Future<Result<FileNode>> future = createFileUseCase.execute(currentPath.child(name.trim()));
        waitAndRefresh(future, "File created");
    }

    public void rename(FileNode node, String newName) {
        if (node == null || newName == null || newName.isBlank()) {
            return;
        }
        Future<Result<Void>> future = renameFileUseCase.execute(node.path(), newName.trim());
        waitAndRefresh(future, "Renamed");
    }

    public void delete(FileNode node) {
        if (node == null) {
            return;
        }
        Future<Result<Void>> future = deleteFilesUseCase.execute(List.of(node.path()), false);
        waitAndRefresh(future, "Moved to trash");
    }

    public void openTrash() {
        if (currentPath == null) {
            return;
        }
        navigateTo(FilePath.local(storageRoot(currentPath.path()) + "/.AppTrash"));
    }

    public void onItemClicked(FileNode node) {
        if (node.isDirectory()) {
            navigateTo(node.path());
        } else {
            events.postValue("Selected: " + node.name());
        }
    }

    private void load(FilePath path) {
        if (pendingLoad != null) {
            pendingLoad.cancel(true);
        }
        uiState.setValue(new UiState.Loading());
        pendingLoad = listDirectoryUseCase.execute(path);
        new Thread(() -> {
            try {
                @SuppressWarnings("unchecked")
                Result<List<FileNode>> result = (Result<List<FileNode>>) pendingLoad.get();
                if (result.isSuccess()) {
                    List<FileNode> nodes = result.getOrNull();
                    uiState.postValue(nodes.isEmpty()
                            ? new UiState.Empty(path)
                            : new UiState.Content(path, nodes));
                } else {
                    uiState.postValue(new UiState.Error(path, result.errorOrNull().getMessage()));
                }
            } catch (Throwable e) {
                uiState.postValue(new UiState.Error(path, e.getMessage()));
            }
        }, "file-browser-load").start();
    }

    private void waitForFolderCreation(Future<Result<FileNode>> future) {
        waitAndRefresh(future, "Folder created");
    }

    private <T> void waitAndRefresh(Future<Result<T>> future, String successMessage) {
        new Thread(() -> {
            try {
                Result<T> result = future.get();
                if (result.isSuccess()) {
                    events.postValue(successMessage);
                    refresh();
                } else {
                    events.postValue(result.errorOrNull().getMessage());
                }
            } catch (Throwable e) {
                events.postValue(e.getMessage());
            }
        }, "file-browser-action").start();
    }

    private static String storageRoot(String path) {
        if (path.startsWith("/storage/emulated/0")) {
            return "/storage/emulated/0";
        }
        if (path.startsWith("/sdcard")) {
            return "/sdcard";
        }
        return "/";
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
                java.io.File file = new java.io.File(path.path());
                this.freeBytes = file.getUsableSpace();
                this.totalBytes = file.getTotalSpace();
            }
        }

        public static final class Empty extends UiState {
            public final FilePath path;

            public Empty(FilePath path) {
                this.path = path;
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

package com.vpt.filemanager.component.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.entry.SortOption;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.settings.UserPreferences;
import com.vpt.filemanager.component.content.OpenedContent;
import com.vpt.filemanager.component.pane.PaneId;
import com.vpt.filemanager.component.pane.PaneState;
import com.vpt.filemanager.storage.facade.Capability;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Shared observable browser state. Components modify it explicitly; it performs no filesystem,
 * archive, storage-facade or Android-view work.
 */
@HiltViewModel
public final class StateViewModel extends ViewModel {
    private final MutablePane left;
    private final MutablePane right;
    private final MutableLiveData<PaneState> leftState = new MutableLiveData<>();
    private final MutableLiveData<PaneState> rightState = new MutableLiveData<>();
    private final MutableLiveData<PaneId> active = new MutableLiveData<>(PaneId.LEFT);
    private final MutableLiveData<OpenedContent> content = new MutableLiveData<>();
    private final MutableLiveData<Long> visibleRefresh = new MutableLiveData<>(0L);
    private long refreshSequence;

    @Inject
    public StateViewModel(UserPreferences preferences) {
        SortOption initialSort = preferences.sortOption();
        left = new MutablePane(Path.storageRoot(), initialSort);
        right = new MutablePane(Path.storageRoot(), initialSort);
        publish(PaneId.LEFT);
        publish(PaneId.RIGHT);
    }

    public LiveData<PaneState> pane(PaneId pane) {
        return pane == PaneId.LEFT ? leftState : rightState;
    }

    public PaneState current(PaneId pane) {
        return value(pane).snapshot();
    }

    public LiveData<PaneId> activePane() {
        return active;
    }

    public PaneId activePaneValue() {
        PaneId current = active.getValue();
        return current == null ? PaneId.LEFT : current;
    }

    public PaneState activeState() {
        return current(activePaneValue());
    }

    public PaneState inactiveState() {
        return current(activePaneValue().other());
    }

    public LiveData<OpenedContent> content() {
        return content;
    }

    @Nullable
    public OpenedContent contentValue() {
        return content.getValue();
    }

    public LiveData<Long> visibleRefresh() {
        return visibleRefresh;
    }

    public void activate(@NonNull PaneId pane) {
        active.setValue(pane);
    }

    public void navigate(@NonNull PaneId pane, @NonNull Path target) {
        MutablePane value = value(pane);
        if (target.equals(value.location)) return;
        value.back.push(value.location);
        value.forward.clear();
        value.location = target;
        value.resetRows();
        if (content.getValue() != null && content.getValue().pane() == pane) {
            content.setValue(null);
        }
        publish(pane);
    }

    public boolean back(@NonNull PaneId pane) {
        MutablePane value = value(pane);
        if (value.back.isEmpty()) return false;
        value.forward.push(value.location);
        value.location = value.back.pop();
        value.resetRows();
        if (content.getValue() != null && content.getValue().pane() == pane) {
            content.setValue(null);
        }
        publish(pane);
        return true;
    }

    public boolean forward(@NonNull PaneId pane) {
        MutablePane value = value(pane);
        if (value.forward.isEmpty()) return false;
        value.back.push(value.location);
        value.location = value.forward.pop();
        value.resetRows();
        content.setValue(null);
        publish(pane);
        return true;
    }

    public void up(@NonNull PaneId pane) {
        Path parent = value(pane).location.parent();
        if (parent != null) navigate(pane, parent);
    }

    public long beginLoading(@NonNull PaneId pane, @NonNull Path requested) {
        MutablePane value = value(pane);
        if (!requested.equals(value.location)) return -1L;
        value.loading = true;
        if (!value.keepErrorForNextRender) value.error = null;
        long generation = ++value.generation;
        publish(pane);
        return generation;
    }

    public void showEntries(@NonNull PaneId pane, long request, @NonNull List<Entry> entries) {
        showDirectory(pane, request, value(pane).location, entries, value(pane).capabilities);
    }

    public void showDirectory(@NonNull PaneId pane, long request, @NonNull Path canonicalPath,
                              @NonNull List<Entry> entries,
                              @NonNull EnumSet<Capability> capabilities) {
        MutablePane value = value(pane);
        if (request != value.generation) return;
        value.location = canonicalPath;
        value.loading = false;
        if (!value.keepErrorForNextRender) value.error = null;
        value.keepErrorForNextRender = false;
        value.capabilities = capabilities.clone();
        value.entries = List.copyOf(entries);
        value.selection.retainAll(entries.stream().map(Entry::key).toList());
        publish(pane);
    }

    public void showFailure(@NonNull PaneId pane, long request, @Nullable String error) {
        MutablePane value = value(pane);
        if (request != value.generation) return;
        value.loading = false;
        value.error = error == null ? "Unable to open location" : error;
        value.entries = List.of();
        value.selection.clear();
        publish(pane);
    }

    /** Returns from a failed file open without placing the failed file into navigation history. */
    public void returnFromOpenedFile(@NonNull PaneId pane, long request, @NonNull Path file,
                                     @Nullable String error) {
        MutablePane value = value(pane);
        if (request != value.generation || !file.equals(value.location)) return;
        Path parent = file.parent();
        if (parent == null) {
            showFailure(pane, request, error);
            return;
        }
        if (!value.back.isEmpty() && parent.equals(value.back.peek())) value.back.pop();
        value.location = parent;
        value.resetRows();
        value.error = error;
        value.keepErrorForNextRender = error != null;
        publish(pane);
    }

    public void showContent(@NonNull OpenedContent result) {
        if (result.source().equals(value(result.pane()).location)) {
            content.setValue(result);
        }
    }

    public void setSort(@NonNull PaneId pane, @NonNull SortOption sort) {
        value(pane).sort = sort;
        publish(pane);
        refreshVisiblePanes();
    }

    public void toggleSelection(@NonNull PaneId pane, @NonNull Entry entry) {
        if (entry.isParent()) return;
        MutablePane value = value(pane);
        value.selectionMode = true;
        if (!value.selection.add(entry.key())) value.selection.remove(entry.key());
        publish(pane);
    }

    public void selectAll(@NonNull PaneId pane) {
        MutablePane value = value(pane);
        value.selectionMode = true;
        value.selection.clear();
        for (Entry entry : value.entries) if (!entry.isParent()) value.selection.add(entry.key());
        publish(pane);
    }

    public void selectRange(@NonNull PaneId pane) {
        MutablePane value = value(pane);
        if (value.selection.size() < 2) return;
        int first = Integer.MAX_VALUE;
        int last = -1;
        for (int index = 0; index < value.entries.size(); index++) {
            if (value.selection.contains(value.entries.get(index).key())) {
                first = Math.min(first, index);
                last = Math.max(last, index);
            }
        }
        for (int index = first; index <= last && index < value.entries.size(); index++) {
            if (!value.entries.get(index).isParent()) value.selection.add(value.entries.get(index).key());
        }
        publish(pane);
    }

    public void clearSelection(@NonNull PaneId pane, boolean exitMode) {
        MutablePane value = value(pane);
        value.selection.clear();
        if (exitMode) value.selectionMode = false;
        publish(pane);
    }

    public void refreshVisiblePanes() {
        visibleRefresh.setValue(++refreshSequence);
    }

    private MutablePane value(PaneId pane) {
        return pane == PaneId.LEFT ? left : right;
    }

    private void publish(PaneId pane) {
        (pane == PaneId.LEFT ? leftState : rightState).setValue(value(pane).snapshot());
    }

    private static final class MutablePane {
        private final ArrayDeque<Path> back = new ArrayDeque<>();
        private final ArrayDeque<Path> forward = new ArrayDeque<>();
        private final LinkedHashSet<String> selection = new LinkedHashSet<>();
        private Path location;
        private List<Entry> entries = List.of();
        private SortOption sort;
        private boolean loading = true;
        private String error;
        private boolean selectionMode;
        private long generation;
        private EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
        private boolean keepErrorForNextRender;

        MutablePane(Path location, SortOption sort) {
            this.location = location;
            this.sort = sort;
        }

        void resetRows() {
            entries = List.of();
            selection.clear();
            selectionMode = false;
            loading = true;
            error = null;
            keepErrorForNextRender = false;
        }

        PaneState snapshot() {
            return new PaneState(location, entries, selection, sort, loading, error,
                    selectionMode, !back.isEmpty(), !forward.isEmpty(), capabilities);
        }
    }
}

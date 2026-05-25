package com.vpt.filemanager.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.vpt.filemanager.storage.persistence.UserPreferences;
import com.vpt.filemanager.model.Entry;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.model.SortOption;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Shared observable browser state. Components modify it explicitly; it performs no filesystem,
 * archive, resolver or Android-view work.
 */
@HiltViewModel
public final class StateViewModel extends ViewModel {
    private final MutablePane left;
    private final MutablePane right;
    private final MutableLiveData<PaneState> leftState = new MutableLiveData<>();
    private final MutableLiveData<PaneState> rightState = new MutableLiveData<>();
    private final MutableLiveData<PaneId> active = new MutableLiveData<>(PaneId.LEFT);
    private final MutableLiveData<ContentState> content = new MutableLiveData<>();
    private final MutableLiveData<Long> invalidation = new MutableLiveData<>(0L);
    private long invalidateSequence;

    @Inject
    public StateViewModel(UserPreferences preferences) {
        SortOption initialSort = preferences.sortOption();
        left = new MutablePane(Location.storageRoot(), initialSort);
        right = new MutablePane(Location.storageRoot(), initialSort);
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

    public LiveData<ContentState> content() {
        return content;
    }

    @Nullable
    public ContentState contentValue() {
        return content.getValue();
    }

    public LiveData<Long> invalidation() {
        return invalidation;
    }

    public void activate(@NonNull PaneId pane) {
        active.setValue(pane);
    }

    public void navigate(@NonNull PaneId pane, @NonNull Location target) {
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

    /** Replaces a file location with its resolved archive mount without creating a history step. */
    public void replaceResolvedLocation(@NonNull PaneId pane, @NonNull Location target) {
        MutablePane value = value(pane);
        value.location = target;
        value.resetRows();
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
        Location parent = value(pane).location.parent();
        if (parent != null) navigate(pane, parent);
    }

    public long beginLoading(@NonNull PaneId pane, @NonNull Location requested) {
        MutablePane value = value(pane);
        if (!requested.equals(value.location)) return -1L;
        value.loading = true;
        value.error = null;
        long generation = ++value.generation;
        publish(pane);
        return generation;
    }

    public void showEntries(@NonNull PaneId pane, long request, @NonNull List<Entry> entries) {
        MutablePane value = value(pane);
        if (request != value.generation) return;
        value.loading = false;
        value.error = null;
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

    public void showContent(@NonNull ContentState result) {
        if (result.source().equals(value(result.pane()).location)) {
            content.setValue(result);
        }
    }

    public void setSort(@NonNull PaneId pane, @NonNull SortOption sort) {
        value(pane).sort = sort;
        publish(pane);
        invalidate(value(pane).location);
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

    public void invalidate(@NonNull Location ignoredChangedLocation) {
        invalidation.setValue(++invalidateSequence);
    }

    private MutablePane value(PaneId pane) {
        return pane == PaneId.LEFT ? left : right;
    }

    private void publish(PaneId pane) {
        (pane == PaneId.LEFT ? leftState : rightState).setValue(value(pane).snapshot());
    }

    private static final class MutablePane {
        private final ArrayDeque<Location> back = new ArrayDeque<>();
        private final ArrayDeque<Location> forward = new ArrayDeque<>();
        private final LinkedHashSet<String> selection = new LinkedHashSet<>();
        private Location location;
        private List<Entry> entries = List.of();
        private SortOption sort;
        private boolean loading = true;
        private String error;
        private boolean selectionMode;
        private long generation;

        MutablePane(Location location, SortOption sort) {
            this.location = location;
            this.sort = sort;
        }

        void resetRows() {
            entries = List.of();
            selection.clear();
            selectionMode = false;
            loading = true;
            error = null;
        }

        PaneState snapshot() {
            return new PaneState(location, entries, selection, sort, loading, error,
                    selectionMode, !back.isEmpty(), !forward.isEmpty());
        }
    }
}

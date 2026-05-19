package com.vpt.filemanager.ui.trash;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

import com.vpt.filemanager.core.AppExecutors;
import com.vpt.filemanager.domain.model.TrashEntry;
import com.vpt.filemanager.domain.repository.TrashRepository;
import com.vpt.filemanager.ui.LiveEvent;

/**
 * State + actions for the trash screen. Entries come straight from {@link TrashRepository} as
 * Room-backed LiveData, so inserts/restores from anywhere in the app re-emit automatically. The
 * VM only owns io-scheduling and a one-shot event channel for toast messages.
 */
@HiltViewModel
public final class TrashViewModel extends ViewModel {
    private final TrashRepository repository;
    private final AppExecutors executors;
    private final LiveEvent<String> events = new LiveEvent<>();

    @Inject
    public TrashViewModel(TrashRepository repository, AppExecutors executors) {
        this.repository = repository;
        this.executors = executors;
    }

    @NonNull
    public LiveData<List<TrashEntry>> entries() {
        return repository.entriesLive();
    }

    public LiveData<String> events() {
        return events;
    }

    public void restore(@NonNull String entryId) {
        executors.io().submit(() -> {
            try {
                repository.restore(entryId);
                events.postValue("Restored");
            } catch (Throwable t) {
                events.postValue(t.getMessage() == null ? "Restore failed" : t.getMessage());
            }
        });
    }

    public void empty() {
        executors.io().submit(() -> {
            try {
                repository.empty();
                events.postValue("Trash emptied");
            } catch (Throwable t) {
                events.postValue(t.getMessage() == null ? "Empty failed" : t.getMessage());
            }
        });
    }
}

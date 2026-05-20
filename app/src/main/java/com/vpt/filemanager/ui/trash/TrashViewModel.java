package com.vpt.filemanager.ui.trash;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

import com.vpt.filemanager.core.AppExecutors;
import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.data.db.entity.TrashEntryEntity;
import com.vpt.filemanager.operations.TrashOps;
import com.vpt.filemanager.ui.LiveEvent;

/**
 * State + actions cho trash screen. Phase R-6: migrated từ {@code TrashRepository} (interface
 * legacy) sang {@link TrashOps} (R-4 orchestrator) + {@link TrashDao} (observe). Entry rows là
 * {@link TrashEntryEntity} trực tiếp — domain {@code TrashEntry} POJO xóa.
 *
 * <p>VM chỉ giữ io-scheduling + one-shot event channel cho toast messages.
 */
@HiltViewModel
public final class TrashViewModel extends ViewModel {
    private final TrashDao dao;
    private final TrashOps trashOps;
    private final AppExecutors executors;
    private final LiveEvent<String> events = new LiveEvent<>();

    @Inject
    public TrashViewModel(TrashDao dao, TrashOps trashOps, AppExecutors executors) {
        this.dao = dao;
        this.trashOps = trashOps;
        this.executors = executors;
    }

    @NonNull
    public LiveData<List<TrashEntryEntity>> entries() {
        return dao.observeAll();
    }

    public LiveData<String> events() {
        return events;
    }

    public void restore(@NonNull String entryId) {
        executors.io().submit(() -> {
            try {
                trashOps.restore(entryId);
                events.postValue("Restored");
            } catch (Throwable t) {
                events.postValue(t.getMessage() == null ? "Restore failed" : t.getMessage());
            }
        });
    }

    public void empty() {
        executors.io().submit(() -> {
            try {
                trashOps.emptyAll();
                events.postValue("Trash emptied");
            } catch (Throwable t) {
                events.postValue(t.getMessage() == null ? "Empty failed" : t.getMessage());
            }
        });
    }
}

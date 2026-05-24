package com.vpt.filemanager.browser.workspace;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionDispatcher;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.action.entry.CreateEntryAction;
import com.vpt.filemanager.core.error.NameConflictException;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.source.ItemFetcher;
import com.vpt.filemanager.data.persistence.UserPreferences;
import com.vpt.filemanager.browser.rule.RuleEngine;
import com.vpt.filemanager.browser.rule.StorageBoundary;
import com.vpt.filemanager.core.threading.AppExecutors;
import com.vpt.filemanager.browser.workspace.effect.LiveEvent;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.reconcile.ChangeSet;
import com.vpt.filemanager.browser.workspace.reconcile.VisibleLocationWatcher;
import com.vpt.filemanager.browser.workspace.state.PaneCoordinator;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

/** Single control center for dual-pane state. UI observes and dispatches Actions only. */
@HiltViewModel
public final class WorkspaceCoordinator extends ViewModel {
    private final ItemFetcher fetcher;
    private final VisibleLocationWatcher watcher;
    private final AppExecutors executors;
    private final RuleEngine rules;
    private final ActionDispatcher dispatcher;
    private final PaneCoordinator left;
    private final PaneCoordinator right;
    private final MutableLiveData<WorkspaceSnapshot> state = new MutableLiveData<>();
    private final LiveEvent<WorkspaceEffect> effects = new LiveEvent<>();
    private PaneId active = PaneId.LEFT;

    @Inject
    public WorkspaceCoordinator(ItemFetcher fetcher, VisibleLocationWatcher watcher,
                                AppExecutors executors, UserPreferences preferences,
                                RuleEngine rules, ActionDispatcher dispatcher) {
        this.fetcher = fetcher;
        this.watcher = watcher;
        this.executors = executors;
        this.rules = rules;
        this.dispatcher = dispatcher;
        left = new PaneCoordinator(preferences.sortOrder());
        right = new PaneCoordinator(preferences.sortOrder());
        watcher.setListener(this::onExternalChange);
        openInitial(left);
        openInitial(right);
        publish();
    }

    @NonNull public LiveData<WorkspaceSnapshot> state() { return state; }
    @NonNull public LiveData<WorkspaceEffect> effects() { return effects; }
    public PaneId activePane() { return active; }
    public WorkspaceSnapshot current() { return snapshot(); }

    @NonNull
    public EnumSet<ActionKey> disabledActions() {
        return rules.disabled(snapshot());
    }

    public void dispatch(@NonNull Action action) {
        WorkspaceSnapshot before = snapshot();
        if (isPolicyControlled(action.key()) && !rules.allows(action.key(), before)) {
            effects.setValue(new WorkspaceEffect.Toast("Action is unavailable here"));
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return dispatcher.dispatch(action, before);
            } catch (FileOperationException error) {
                throw new CompletionException(error);
            }
        }, executors.io()).whenComplete((result, failure) -> executors.main().execute(() -> {
            if (failure != null) {
                Throwable reason = failure.getCause() == null ? failure : failure.getCause();
                if (reason instanceof NameConflictException conflict
                        && action instanceof CreateEntryAction create) {
                    effects.setValue(new WorkspaceEffect.ResolveCreateConflict(create, conflict.name()));
                    return;
                }
                effects.setValue(new WorkspaceEffect.Toast(reason == null
                        || reason.getMessage() == null ? "Operation failed" : reason.getMessage()));
                return;
            }
            apply(result);
        }));
    }

    private void apply(ActionResult result) {
        if (result instanceof ActionResult.Activate value) {
            active = value.pane();
            publish();
        } else if (result instanceof ActionResult.Navigate value) {
            navigate(pane(value.pane()), value.path());
        } else if (result instanceof ActionResult.History value) {
            PaneCoordinator pane = pane(value.pane());
            if (value.forward() ? pane.forward() : pane.back()) load(pane);
        } else if (result instanceof ActionResult.Refresh value) {
            load(pane(value.pane()));
        } else if (result instanceof ActionResult.RefreshVisible value) {
            load(left);
            load(right);
            if (value.message() != null) effects.setValue(new WorkspaceEffect.Toast(value.message()));
        } else if (result instanceof ActionResult.Sort value) {
            pane(value.pane()).sort(value.order());
            load(pane(value.pane()));
        } else if (result instanceof ActionResult.ToggleSelection value) {
            pane(value.pane()).toggle(value.item(), value.enterMode());
            publish();
        } else if (result instanceof ActionResult.SelectAll value) {
            pane(value.pane()).selectAll();
            publish();
        } else if (result instanceof ActionResult.SelectRange value) {
            pane(value.pane()).selectRange();
            publish();
        } else if (result instanceof ActionResult.ClearSelection value) {
            pane(value.pane()).clear(value.exitMode());
            publish();
        } else if (result instanceof ActionResult.Effect value) {
            effects.setValue(value.effect());
        } else if (result instanceof ActionResult.Composite value) {
            for (ActionResult change : value.changes()) apply(change);
        }
    }

    private void openInitial(PaneCoordinator pane) {
        pane.open(StorageBoundary.root());
        watcher.retain(StorageBoundary.root());
        load(pane);
    }

    private void navigate(PaneCoordinator pane, Path target) {
        Path previous = pane.location();
        pane.navigate(target);
        if (previous != null && !previous.equals(target)) watcher.release(previous);
        if (!target.equals(previous)) watcher.retain(target);
        load(pane);
    }

    private void load(PaneCoordinator pane) {
        Path path = pane.location();
        if (path == null) return;
        long request = pane.beginLoad();
        publish();
        CompletableFuture.supplyAsync(() -> {
            try {
                return fetcher.fetch(path, pane.sort());
            } catch (FileOperationException error) {
                throw new CompletionException(error);
            }
        }, executors.io()).whenComplete((items, failure) -> executors.main().execute(() -> {
            if (failure == null) {
                pane.loaded(request, items);
            } else {
                Path recovery = pane.recoverDeletedStorageLocation();
                if (recovery != null && !recovery.equals(path)) {
                    watcher.release(path);
                    watcher.retain(recovery);
                    load(pane);
                    return;
                }
                Throwable reason = failure.getCause();
                pane.failed(request, reason == null ? "Unable to read folder" : reason.getMessage());
            }
            publish();
        }));
    }

    private void onExternalChange(ChangeSet changes) {
        if (left.location() != null && changes.affects(left.location())) load(left);
        if (right.location() != null && changes.affects(right.location())) load(right);
    }

    private WorkspaceSnapshot snapshot() {
        return new WorkspaceSnapshot(left.snapshot(), right.snapshot(), active);
    }

    private void publish() {
        state.setValue(snapshot());
    }

    private PaneCoordinator pane(PaneId id) {
        return id == PaneId.LEFT ? left : right;
    }

    private static boolean isPolicyControlled(ActionKey key) {
        return switch (key) {
            case COPY, MOVE, DELETE, RENAME, PROPERTIES, SHARE, OPEN_WITH, BOOKMARK, CREATE,
                    REMOVE_BOOKMARK, RESTORE_TRASH, EMPTY_TRASH -> true;
            default -> false;
        };
    }

    @Override
    protected void onCleared() {
        if (left.location() != null) watcher.release(left.location());
        if (right.location() != null) watcher.release(right.location());
        watcher.setListener(null);
    }
}

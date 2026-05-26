package com.vpt.filemanager.component.bottombar;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;

import com.vpt.filemanager.R;
import com.vpt.filemanager.app.threading.AppExecutors;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.virtual.Capability;
import com.vpt.filemanager.storage.facade.StorageFacade;
import com.vpt.filemanager.storage.facade.TransferDecision;
import com.vpt.filemanager.component.dialog.ConfirmDialogComponent;
import com.vpt.filemanager.component.dialog.ConflictDialogComponent;
import com.vpt.filemanager.component.dialog.InputDialogComponent;
import com.vpt.filemanager.component.dialog.PropertiesDialogComponent;
import com.vpt.filemanager.component.dialog.SelectionActionsDialogComponent;
import com.vpt.filemanager.core.error.NameConflictException;
import com.vpt.filemanager.core.format.MimeTypes;
import com.vpt.filemanager.component.pane.PaneId;
import com.vpt.filemanager.component.pane.PaneState;
import com.vpt.filemanager.state.StateViewModel;

import java.util.ArrayList;
import java.util.List;

/** Bottom bar owns its mode variants and all actions visible within those variants. */
public final class BottomBarComponent {
    private static final float DISABLED_ALPHA = 0.38f;
    private final AppCompatActivity activity;
    private final StateViewModel state;
    private final StorageFacade storage;
    private final AppExecutors executors;
    private final View normal;
    private final View selection;
    private final ImageButton back;
    private final ImageButton forward;
    private final ImageButton add;
    private final ImageButton up;
    private final ImageButton more;

    public BottomBarComponent(AppCompatActivity activity, StateViewModel state,
                              StorageFacade storage, AppExecutors executors) {
        this.activity = activity;
        this.state = state;
        this.storage = storage;
        this.executors = executors;
        normal = activity.findViewById(R.id.bottom_bar);
        selection = activity.findViewById(R.id.selection_bar);
        back = activity.findViewById(R.id.btn_back);
        forward = activity.findViewById(R.id.btn_forward);
        add = activity.findViewById(R.id.btn_add);
        up = activity.findViewById(R.id.btn_up);
        more = activity.findViewById(R.id.btn_sel_more);
    }

    public void attach(LifecycleOwner owner) {
        back.setOnClickListener(view -> state.back(state.activePaneValue()));
        forward.setOnClickListener(view -> state.forward(state.activePaneValue()));
        up.setOnClickListener(view -> state.up(state.activePaneValue()));
        activity.findViewById(R.id.btn_swap).setOnClickListener(view ->
                state.activate(state.activePaneValue().other()));
        add.setOnClickListener(view -> InputDialogComponent.create(activity,
                (folder, name) -> run(() -> storage.create(state.activeState().location, name, folder),
                        folder ? "Folder created" : "File created")));
        activity.findViewById(R.id.btn_sel_all).setOnClickListener(view ->
                state.selectAll(state.activePaneValue()));
        activity.findViewById(R.id.btn_sel_deselect).setOnClickListener(view ->
                state.clearSelection(state.activePaneValue(), false));
        activity.findViewById(R.id.btn_sel_cancel).setOnClickListener(view ->
                state.clearSelection(state.activePaneValue(), true));
        activity.findViewById(R.id.btn_sel_range).setOnClickListener(view ->
                state.selectRange(state.activePaneValue()));
        more.setOnClickListener(view -> showSelectionActions());
        state.activePane().observe(owner, ignored -> render(state.activeState()));
        state.pane(PaneId.LEFT).observe(owner, ignored -> renderIfActive());
        state.pane(PaneId.RIGHT).observe(owner, ignored -> renderIfActive());
    }

    private void renderIfActive() {
        render(state.activeState());
    }

    private void render(PaneState pane) {
        boolean selected = pane.selectionMode;
        normal.setVisibility(selected ? View.GONE : View.VISIBLE);
        selection.setVisibility(selected ? View.VISIBLE : View.GONE);
        if (!selected) {
            enabled(back, pane.canGoBack);
            enabled(forward, pane.canGoForward);
            enabled(up, pane.location.parent() != null);
            enabled(add, pane.capabilities.contains(Capability.CREATE));
            return;
        }
        int count = pane.selection.size();
        enabled(activity.findViewById(R.id.btn_sel_deselect), count > 0);
        enabled(activity.findViewById(R.id.btn_sel_range), count >= 2);
        enabled(more, count > 0);
        more.setImageResource(pane.location.isTrash() ? R.drawable.ic_restore
                : pane.location.isBookmarks() ? R.drawable.ic_bookmark_remove : R.drawable.ic_more);
    }

    private void showSelectionActions() {
        PaneState pane = state.activeState();
        List<Entry> selected = pane.selectedEntries();
        if (selected.isEmpty()) return;
        if (pane.location.isTrash()) {
            run(() -> storage.restore(selected), "Restored");
            return;
        }
        if (pane.location.isBookmarks()) {
            storage.removeBookmarks(selected);
            state.clearSelection(state.activePaneValue(), true);
            state.refreshVisiblePanes();
            return;
        }
        String[] labels = {
                activity.getString(R.string.action_copy),
                activity.getString(R.string.action_move),
                activity.getString(R.string.action_delete),
                activity.getString(R.string.action_rename),
                activity.getString(R.string.action_share),
                activity.getString(R.string.action_open_with),
                activity.getString(R.string.action_bookmark),
                activity.getString(R.string.properties)
        };
        Entry single = selected.size() == 1 ? selected.get(0) : null;
        Path destination = state.inactiveState().location;
        boolean transfer = state.inactiveState().capabilities.contains(Capability.MOVE_IN)
                && !destination.equals(state.activeState().location);
        boolean[] enabled = {
                transfer,
                transfer && pane.capabilities.contains(Capability.MOVE_OUT),
                pane.capabilities.contains(Capability.DELETE),
                single != null && pane.capabilities.contains(Capability.RENAME),
                selected.stream().anyMatch(entry -> !entry.isFolder()),
                single != null && !single.isFolder(),
                single != null && single.isFolder() && !single.isInsideArchive()
                        && single.localPathOrNull() != null,
                single != null && !single.isInsideArchive()
        };
        SelectionActionsDialogComponent.show(activity, single == null
                        ? activity.getString(R.string.selected_count, selected.size()) : single.name(),
                labels, enabled, index -> selectAction(index, selected));
    }

    private void selectAction(int action, List<Entry> selected) {
        Entry single = selected.size() == 1 ? selected.get(0) : null;
        Path destination = state.inactiveState().location;
        if (action == 0 || action == 1) {
            if (destination.equals(state.activeState().location)) {
                toast(activity.getString(R.string.transfer_same_folder));
            } else {
                transfer(selected, destination, action == 1);
            }
        } else if (action == 2) {
            int message = state.activeState().location.isInsideArchive()
                    ? R.string.archive_delete_confirm_count : R.string.delete_confirm_count;
            ConfirmDialogComponent.show(activity, R.string.action_delete,
                    activity.getString(message, selected.size()),
                    () -> run(() -> storage.delete(selected), "Deleted"));
        } else if (action == 3 && single != null) {
            InputDialogComponent.prompt(activity, R.string.action_rename, R.string.file_name, single.name(),
                    value -> run(() -> storage.rename(single, value), "Renamed"));
        } else if (action == 4) {
            share(selected);
        } else if (action == 5 && single != null) {
            openWith(single);
        } else if (action == 6 && single != null) {
            run(() -> storage.bookmark(single), "Bookmarked");
        } else if (action == 7 && single != null && !single.isInsideArchive()) {
            PropertiesDialogComponent.show(activity, single);
        } else {
            toast(activity.getString(R.string.selection_single_only));
        }
    }

    private void share(List<Entry> selected) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (Entry entry : selected) {
            if (entry.isFolder()) continue;
            try {
                uris.add(storage.contentUri(entry));
            } catch (Exception error) {
                toast(error.getMessage());
            }
        }
        if (uris.isEmpty()) return;
        Intent send = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE)
                .setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (uris.size() == 1) send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.action_share)));
    }

    private void openWith(Entry entry) {
        try {
            Uri uri = storage.contentUri(entry);
            Intent open = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, MimeTypes.detect(entry.name()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(open, activity.getString(R.string.action_open_with)));
        } catch (ActivityNotFoundException | IllegalArgumentException | com.vpt.filemanager.core.error.FileOperationException error) {
            toast(activity.getString(R.string.unavailable));
        }
    }

    private void transfer(List<Entry> selected, Path destination, boolean move) {
        transferNext(selected, destination, move, 0, null);
    }

    private void transferNext(List<Entry> selected, Path destination, boolean move, int index,
                              @Nullable TransferDecision remainingDecision) {
        if (index >= selected.size()) {
            finishTransfer(move ? "Moved" : "Copied");
            return;
        }
        Entry source = selected.get(index);
        TransferDecision decision = remainingDecision == null
                ? TransferDecision.ASK : remainingDecision;
        executors.io().execute(() -> {
            try {
                storage.transfer(source, destination, move, decision);
                executors.main().execute(() ->
                        transferNext(selected, destination, move, index + 1, remainingDecision));
            } catch (NameConflictException conflict) {
                executors.main().execute(() -> ConflictDialogComponent.show(activity,
                        conflict.name(), (choice, applyAll) -> {
                            if (choice == TransferDecision.CANCEL) {
                                state.refreshVisiblePanes();
                                return;
                            }
                            retryTransfer(selected, destination, move, index, choice,
                                    applyAll ? choice : null);
                        }));
            } catch (Exception error) {
                executors.main().execute(() -> toast(error.getMessage()));
            }
        });
    }

    private void retryTransfer(List<Entry> selected, Path destination, boolean move, int index,
                               TransferDecision choice,
                               @Nullable TransferDecision remainingDecision) {
        executors.io().execute(() -> {
            try {
                storage.transfer(selected.get(index), destination, move, choice);
                executors.main().execute(() ->
                        transferNext(selected, destination, move, index + 1, remainingDecision));
            } catch (Exception error) {
                executors.main().execute(() -> toast(error.getMessage()));
            }
        });
    }

    private void finishTransfer(String success) {
        state.clearSelection(state.activePaneValue(), true);
        state.refreshVisiblePanes();
        toast(success);
    }

    private void run(Throwing operation, String success) {
        executors.io().execute(() -> {
            try {
                operation.run();
                executors.main().execute(() -> {
                    state.clearSelection(state.activePaneValue(), true);
                    state.refreshVisiblePanes();
                    toast(success);
                });
            } catch (Exception error) {
                executors.main().execute(() -> toast(error.getMessage()));
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(activity, message == null ? activity.getString(R.string.error_unknown) : message,
                Toast.LENGTH_SHORT).show();
    }

    private static void enabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : DISABLED_ALPHA);
    }

    private interface Throwing {
        void run() throws Exception;
    }
}

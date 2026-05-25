package com.vpt.filemanager.ui.bottombar;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LifecycleOwner;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.threading.AppExecutors;
import com.vpt.filemanager.entry.Entry;
import com.vpt.filemanager.navigation.Location;
import com.vpt.filemanager.operation.FileOperations;
import com.vpt.filemanager.ui.dialog.InputDialogs;
import com.vpt.filemanager.ui.format.MimeTypes;
import com.vpt.filemanager.ui.pane.PaneId;
import com.vpt.filemanager.ui.pane.PaneState;
import com.vpt.filemanager.ui.state.StateViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Bottom bar owns its mode variants and all actions visible within those variants. */
public final class BottomBarComponent {
    private static final float DISABLED_ALPHA = 0.38f;
    private final AppCompatActivity activity;
    private final StateViewModel state;
    private final FileOperations operations;
    private final AppExecutors executors;
    private final View normal;
    private final View selection;
    private final ImageButton back;
    private final ImageButton forward;
    private final ImageButton add;
    private final ImageButton up;
    private final ImageButton more;

    public BottomBarComponent(AppCompatActivity activity, StateViewModel state,
                              FileOperations operations, AppExecutors executors) {
        this.activity = activity;
        this.state = state;
        this.operations = operations;
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
        add.setOnClickListener(view -> InputDialogs.create(activity,
                (folder, name) -> run(() -> operations.create(state.activeState().location, name, folder),
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
            enabled(add, operations.canWrite(pane.location));
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
            run(() -> operations.restore(selected), "Restored");
            return;
        }
        if (pane.location.isBookmarks()) {
            operations.removeBookmarks(selected);
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
        Location destination = state.inactiveState().location;
        boolean transfer = operations.canWrite(destination)
                && !destination.equals(state.activeState().location);
        boolean[] enabled = {
                transfer,
                transfer,
                true,
                single != null,
                selected.stream().anyMatch(entry -> !entry.isFolder()),
                single != null && !single.isFolder(),
                single != null && single.isFolder() && !single.isArchiveEntry()
                        && single.localPathOrNull() != null,
                single != null && !single.isArchiveEntry()
        };
        new AlertDialog.Builder(activity).setTitle(single == null
                        ? activity.getString(R.string.selected_count, selected.size()) : single.name())
                .setAdapter(new SelectionActionsAdapter(labels, enabled),
                        (dialog, index) -> selectAction(index, selected)).show();
    }

    private void selectAction(int action, List<Entry> selected) {
        Entry single = selected.size() == 1 ? selected.get(0) : null;
        Location destination = state.inactiveState().location;
        if (action == 0 || action == 1) {
            if (destination.equals(state.activeState().location)) {
                toast(activity.getString(R.string.transfer_same_folder));
            } else {
                run(() -> operations.transfer(selected, destination, action == 1),
                        action == 1 ? "Moved" : "Copied");
            }
        } else if (action == 2) {
            new AlertDialog.Builder(activity).setTitle(R.string.action_delete)
                    .setMessage(activity.getString(R.string.delete_confirm_count, selected.size()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> run(() -> operations.delete(selected), "Deleted")).show();
        } else if (action == 3 && single != null) {
            InputDialogs.prompt(activity, R.string.action_rename, R.string.file_name, single.name(),
                    value -> run(() -> operations.rename(single, value), "Renamed"));
        } else if (action == 4) {
            share(selected);
        } else if (action == 5 && single != null) {
            openWith(single);
        } else if (action == 6 && single != null) {
            run(() -> operations.bookmark(single), "Bookmarked");
        } else if (action == 7 && single != null && !single.isArchiveEntry()) {
            showProperties(single);
        } else {
            toast(activity.getString(R.string.selection_single_only));
        }
    }

    private void share(List<Entry> selected) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (Entry entry : selected) {
            if (entry.isFolder()) continue;
            try {
                String path = operations.materializeIfRequired(entry);
                uris.add(FileProvider.getUriForFile(activity,
                        activity.getPackageName() + ".fileprovider", new File(path)));
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
            String path = operations.materializeIfRequired(entry);
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", new File(path));
            Intent open = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, MimeTypes.detect(entry.name()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(open, activity.getString(R.string.action_open_with)));
        } catch (ActivityNotFoundException | IllegalArgumentException | com.vpt.filemanager.core.error.FileOperationException error) {
            toast(activity.getString(R.string.unavailable));
        }
    }

    private void showProperties(Entry entry) {
        new AlertDialog.Builder(activity).setTitle(R.string.properties)
                .setMessage(entry.name() + "\n" + entry.localPath() + "\n"
                        + com.vpt.filemanager.ui.format.ByteSize.format(entry.size()))
                .setPositiveButton(android.R.string.ok, null).show();
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

    private final class SelectionActionsAdapter extends ArrayAdapter<String> {
        private final boolean[] enabledRows;

        SelectionActionsAdapter(String[] labels, boolean[] enabledRows) {
            super(activity, android.R.layout.simple_list_item_1, labels);
            this.enabledRows = enabledRows;
        }

        @Override public boolean isEnabled(int position) {
            return enabledRows[position];
        }

        @Override @NonNull
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = super.getView(position, convertView, parent);
            boolean enabled = isEnabled(position);
            row.setEnabled(enabled);
            row.setAlpha(enabled ? 1f : DISABLED_ALPHA);
            return row;
        }
    }

    private interface Throwing {
        void run() throws Exception;
    }
}

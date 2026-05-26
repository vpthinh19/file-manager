package com.vpt.filemanager.ui.topbar;

import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.appbar.MaterialToolbar;
import com.vpt.filemanager.R;
import com.vpt.filemanager.core.threading.AppExecutors;
import com.vpt.filemanager.core.entry.SortOption;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.operation.FileOperations;
import com.vpt.filemanager.ui.dialog.InputDialogs;
import com.vpt.filemanager.ui.drawer.DrawerComponent;
import com.vpt.filemanager.ui.pane.PaneId;
import com.vpt.filemanager.ui.pane.PaneState;
import com.vpt.filemanager.ui.state.StateViewModel;

import java.io.File;

/** Owns toolbar presentation and its menu commands. */
public final class TopBarComponent {
    private final AppCompatActivity activity;
    private final StateViewModel state;
    private final FileOperations operations;
    private final AppExecutors executors;
    private final DrawerComponent drawer;
    private final MaterialToolbar toolbar;
    private final TextView title;
    private final TextView subtitle;

    public TopBarComponent(AppCompatActivity activity, StateViewModel state,
                           FileOperations operations, AppExecutors executors,
                           DrawerComponent drawer) {
        this.activity = activity;
        this.state = state;
        this.operations = operations;
        this.executors = executors;
        this.drawer = drawer;
        toolbar = activity.findViewById(R.id.toolbar);
        title = activity.findViewById(R.id.tv_toolbar_title);
        subtitle = activity.findViewById(R.id.tv_toolbar_subtitle);
    }

    public void attach(LifecycleOwner owner) {
        toolbar.setNavigationOnClickListener(view -> drawer.open());
        toolbar.inflateMenu(R.menu.menu_dual_pane_overflow);
        Drawable icon = AppCompatResources.getDrawable(activity, R.drawable.ic_more);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(icon, ContextCompat.getColor(activity, R.color.md_chrome_on_bg));
            toolbar.setOverflowIcon(icon);
        }
        toolbar.setOnMenuItemClickListener(this::onMenu);
        state.activePane().observe(owner, ignored -> render(state.activeState()));
        state.pane(PaneId.LEFT).observe(owner, ignored -> refreshIfActive());
        state.pane(PaneId.RIGHT).observe(owner, ignored -> refreshIfActive());
    }

    private boolean onMenu(MenuItem menu) {
        int id = menu.getItemId();
        if (id == R.id.action_refresh) {
            state.refreshVisiblePanes();
        } else if (id == R.id.action_search) {
            Path scope = state.activeState().location;
            if (scope.isInsideArchive()) scope = Path.storage(scope.storagePath());
            if (!scope.isStorage() || scope.isInsideArchive()) scope = Path.storageRoot();
            Path finalScope = scope;
            InputDialogs.prompt(activity, R.string.action_search, R.string.search_files_hint, "",
                    query -> state.navigate(state.activePaneValue(),
                            Path.search(finalScope.storagePath(), query)));
        } else if (id == R.id.action_sort) {
            showSort();
        } else if (id == R.id.action_empty_trash) {
            new AlertDialog.Builder(activity).setTitle(R.string.trash_empty_title)
                    .setMessage(R.string.trash_empty_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            run(() -> operations.emptyTrash(), "Trash emptied"))
                    .show();
        } else if (id == R.id.action_exit) {
            activity.finishAffinity();
        } else if (id == R.id.action_settings) {
            Toast.makeText(activity, R.string.coming_soon, Toast.LENGTH_SHORT).show();
        } else {
            return false;
        }
        return true;
    }

    private void showSort() {
        int[] labels = {R.string.sort_name_asc, R.string.sort_name_desc, R.string.sort_size_desc,
                R.string.sort_size_asc, R.string.sort_date_desc, R.string.sort_date_asc,
                R.string.sort_type};
        SortOption[] values = SortOption.values();
        String[] items = new String[labels.length];
        for (int i = 0; i < labels.length; i++) items[i] = activity.getString(labels[i]);
        new AlertDialog.Builder(activity).setTitle(R.string.sort_title)
                .setItems(items, (dialog, which) ->
                        state.setSort(state.activePaneValue(), values[which])).show();
    }

    private void refreshIfActive() {
        render(state.activeState());
    }

    private void render(PaneState pane) {
        Path location = pane.location;
        MenuItem empty = toolbar.getMenu().findItem(R.id.action_empty_trash);
        if (empty != null) empty.setVisible(location.isTrash());
        if (pane.selectionMode) {
            title.setText(activity.getString(R.string.selected_count, pane.selection.size()));
            subtitle.setText("");
            return;
        }
        title.setText(display(location));
        if (pane.error != null) subtitle.setText(pane.error);
        else if (location.isSearch()) {
            subtitle.setText(activity.getString(R.string.stats_search_results,
                    pane.folderCount + pane.fileCount));
        } else subtitle.setText(activity.getString(R.string.stats_basic, pane.folderCount, pane.fileCount));
    }

    private String display(Path location) {
        if (location.isTrash()) return activity.getString(R.string.action_trash);
        if (location.isBookmarks()) return activity.getString(R.string.menu_bookmarks);
        if (location.isSearch()) return activity.getString(R.string.search_title, location.query());
        if (location.isInsideArchive()) return new File(location.storagePath()).getName()
                + location.archiveInnerPath();
        String virtual = location.serialize();
        return "storage:".equals(virtual) ? activity.getString(R.string.menu_storage) : virtual;
    }

    private void run(Throwing task, String success) {
        executors.io().execute(() -> {
            try {
                task.run();
                executors.main().execute(() -> {
                    state.refreshVisiblePanes();
                    Toast.makeText(activity, success, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                executors.main().execute(() ->
                        Toast.makeText(activity, error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private interface Throwing {
        void run() throws Exception;
    }
}

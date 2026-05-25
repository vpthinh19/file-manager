package com.vpt.filemanager.ui.drawer;

import android.content.res.Configuration;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.navigation.NavigationView;
import com.vpt.filemanager.R;
import com.vpt.filemanager.settings.UserPreferences;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.ui.pane.PaneId;
import com.vpt.filemanager.ui.state.StateViewModel;

/** Owns drawer UI and writes root navigation into whichever pane is active. */
public final class DrawerComponent {
    private final AppCompatActivity activity;
    private final StateViewModel state;
    private final UserPreferences preferences;
    private final DrawerLayout drawer;
    private final NavigationView navigation;

    public DrawerComponent(AppCompatActivity activity, StateViewModel state, UserPreferences preferences) {
        this.activity = activity;
        this.state = state;
        this.preferences = preferences;
        drawer = activity.findViewById(R.id.drawer_layout);
        navigation = activity.findViewById(R.id.nav_view);
    }

    public void attach(LifecycleOwner owner) {
        ImageButton toggle = navigation.getHeaderView(0).findViewById(R.id.btn_toggle_theme);
        renderThemeButton(toggle);
        toggle.setOnClickListener(view -> {
            boolean dark = !isDark();
            preferences.setDarkTheme(dark);
            AppCompatDelegate.setDefaultNightMode(dark
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });
        navigation.setNavigationItemSelectedListener(menu -> {
            if (menu.getItemId() == R.id.menu_storage) {
                state.navigate(state.activePaneValue(), Path.storageRoot());
            } else if (menu.getItemId() == R.id.menu_trash) {
                state.navigate(state.activePaneValue(), Path.trash());
            } else if (menu.getItemId() == R.id.menu_bookmarks) {
                state.navigate(state.activePaneValue(), Path.bookmarks());
            }
            close();
            return true;
        });
        state.activePane().observe(owner, ignored -> renderSelection());
        state.pane(PaneId.LEFT).observe(owner, ignored -> renderSelection());
        state.pane(PaneId.RIGHT).observe(owner, ignored -> renderSelection());
    }

    public void open() {
        drawer.openDrawer(GravityCompat.START);
    }

    public void close() {
        drawer.closeDrawer(GravityCompat.START);
    }

    public boolean closeIfOpen() {
        if (!drawer.isDrawerOpen(GravityCompat.START)) return false;
        close();
        return true;
    }

    public void setLocked(boolean contentVisible) {
        drawer.setDrawerLockMode(contentVisible
                ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    private void renderSelection() {
        Path location = state.activeState().location;
        navigation.setCheckedItem(location.isTrash() ? R.id.menu_trash
                : location.isBookmarks() ? R.id.menu_bookmarks : R.id.menu_storage);
    }

    private void renderThemeButton(ImageButton button) {
        button.setImageResource(isDark() ? R.drawable.ic_light_mode : R.drawable.ic_dark_mode);
        button.setContentDescription(activity.getString(isDark()
                ? R.string.action_use_light_theme : R.string.action_use_dark_theme));
    }

    private boolean isDark() {
        return (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}

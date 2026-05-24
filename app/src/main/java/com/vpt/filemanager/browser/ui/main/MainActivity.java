package com.vpt.filemanager.browser.ui.main;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

import dagger.hilt.android.AndroidEntryPoint;

import com.vpt.filemanager.R;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.rule.StorageBoundary;
import com.vpt.filemanager.browser.ui.drawer.DrawerHost;
import com.vpt.filemanager.browser.ui.pane.DualPaneHostFragment;
import com.vpt.filemanager.data.persistence.UserPreferences;

import javax.inject.Inject;

/** Launcher activity owning the storage-permission gate, navigation drawer and pane host. */
@AndroidEntryPoint
public final class MainActivity extends AppCompatActivity implements DrawerHost {
    private static final String TAG_DUAL_PANE = "dual-pane";

    @Inject UserPreferences preferences;
    private DrawerLayout drawerLayout;
    private boolean contentInflated;
    private final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> resolvePermissionAndContinue());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyThemePreference();
        EdgeToEdge.enable(this);
        if (hasAllFilesAccess()) {
            installContent();
        } else {
            requestAllFilesAccess();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!contentInflated && hasAllFilesAccess()) {
            installContent();
        }
    }

    private void installContent() {
        if (contentInflated) {
            return;
        }
        contentInflated = true;
        setContentView(R.layout.activity_main);
        applyDarkChromeSystemBars();
        drawerLayout = findViewById(R.id.drawer_layout);
        wireDrawerNavigation();
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new DualPaneHostFragment(), TAG_DUAL_PANE)
                    .commit();
        }
        syncDrawerSelection();
    }

    @Override
    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    @Override
    public void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    @Override
    public boolean isDrawerOpen() {
        return drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START);
    }

    private void wireDrawerNavigation() {
        NavigationView navigation = findViewById(R.id.nav_view);
        ImageButton themeToggle = navigation.getHeaderView(0).findViewById(R.id.btn_toggle_theme);
        updateThemeToggle(themeToggle);
        themeToggle.setOnClickListener(view -> {
            boolean dark = !isDarkTheme();
            preferences.setDarkTheme(dark);
            AppCompatDelegate.setDefaultNightMode(dark
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        });
        navigation.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_storage) {
                onStorageSelected();
            } else if (id == R.id.menu_trash) {
                onTrashSelected();
            } else if (id == R.id.menu_bookmarks) {
                onBookmarksSelected();
            }
            closeDrawer();
            return true;
        });
    }

    private void applyThemePreference() {
        Boolean dark = preferences.darkThemeOverride();
        if (dark != null) {
            AppCompatDelegate.setDefaultNightMode(dark
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void updateThemeToggle(ImageButton button) {
        boolean dark = isDarkTheme();
        button.setImageResource(dark ? R.drawable.ic_light_mode : R.drawable.ic_dark_mode);
        button.setContentDescription(getString(dark
                ? R.string.action_use_light_theme : R.string.action_use_dark_theme));
    }

    private boolean isDarkTheme() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void syncDrawerSelection() {
        NavigationView navigation = findViewById(R.id.nav_view);
        if (navigation != null) {
            navigation.setCheckedItem(activeDrawerItemId());
        }
    }

    private int activeDrawerItemId() {
        Fragment hosted = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (hosted instanceof DualPaneHostFragment dual) {
            Path path = dual.activeState().path;
            if (path != null && path.isTrash()) {
                return R.id.menu_trash;
            }
            if (path != null && path.isBookmarks()) {
                return R.id.menu_bookmarks;
            }
        }
        return R.id.menu_storage;
    }

    public void onStorageSelected() {
        navigateActiveIfHosted(StorageBoundary.root());
    }

    public void onTrashSelected() {
        navigateActiveIfHosted(Path.trash());
    }

    public void onBookmarksSelected() {
        navigateActiveIfHosted(Path.bookmarks());
    }

    private void navigateActiveIfHosted(Path target) {
        Fragment hosted = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (hosted instanceof DualPaneHostFragment dual) {
            dual.navigateActivePaneTo(target);
        }
    }

    private void applyDarkChromeSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    private boolean hasAllFilesAccess() {
        return Environment.isExternalStorageManager();
    }

    private void requestAllFilesAccess() {
        Intent specific = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:" + getPackageName()));
        if (tryLaunchSettings(specific)) {
            return;
        }
        if (tryLaunchSettings(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))) {
            return;
        }
        Toast.makeText(this,
                "All-files access settings not available on this device", Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean tryLaunchSettings(Intent intent) {
        try {
            settingsLauncher.launch(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void resolvePermissionAndContinue() {
        if (hasAllFilesAccess()) {
            installContent();
        } else {
            finish();
        }
    }
}

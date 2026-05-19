package com.vpt.filemanager.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.ui.DrawerActionHandler;
import com.vpt.filemanager.ui.DrawerHost;
import com.vpt.filemanager.ui.browser.DualPaneHostFragment;

/**
 * Single launcher activity. Owns:
 * <ul>
 *   <li>the all-files-access permission gate (used to live in its own translucent activity — the
 *       extra hop cost ~150ms cold-start and gave MIUI a chance to flash a wallpaper-tinted status
 *       bar during the launcher→gate→main transition),</li>
 *   <li>the drawer (Storage / Trash / Bookmarks / Settings),</li>
 *   <li>and the {@link DualPaneHostFragment} content frame.</li>
 * </ul>
 *
 * <p>System bars stay dark (chrome) in both light and dark themes — see
 * {@link #applyDarkChromeSystemBars()}.
 */
@AndroidEntryPoint
public final class MainActivity extends AppCompatActivity implements DrawerHost {
    private DrawerLayout drawerLayout;
    /**
     * Tracks whether {@link #installContent()} has run for the current activity instance. Used to
     * make {@link #installContent()} idempotent across onCreate and onResume re-entries — without
     * it, returning from Settings could double-inflate the dual-pane fragment.
     */
    private boolean contentInflated;

    /**
     * Settings round-trip launcher. Using {@code ActivityResultLauncher} (not raw
     * {@code startActivity}) means that when the user presses back from the system settings page,
     * control returns to us — we re-check permission and either proceed or finish, instead of
     * dumping the user at the launcher.
     */
    private final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> resolvePermissionAndContinue());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        if (hasAllFilesAccess()) {
            installContent();
        } else {
            // Skip the inflate cost while we wait for the Settings round-trip. The window background
            // (chrome color) is what the user briefly sees while Settings launches — better than
            // flashing a half-rendered dual-pane skeleton on the critical path.
            requestAllFilesAccess();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Belt-and-braces: catch the case where the user granted permission via a different path
        // (system Files Access screen, ADB) or where the launcher callback raced with activity
        // restart (the "black screen on back" symptom on some MIUI builds — the launcher callback
        // didn't always fire, leaving the activity content-less). Idempotent thanks to
        // contentInflated, so it's safe to invoke on every resume.
        if (!contentInflated && hasAllFilesAccess()) {
            installContent();
        }
    }

    /**
     * Inflate the activity content + wire the drawer + install the dual-pane fragment. Idempotent
     * so onCreate, onResume, and the Settings callback can all converge on a single setup path.
     */
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
                    .replace(R.id.fragment_container, new DualPaneHostFragment())
                    .commit();
        }
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
        NavigationView navView = findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(item -> {
            DrawerActionHandler handler = currentDrawerHandler();
            int id = item.getItemId();
            if (id == R.id.menu_storage) {
                if (handler != null) handler.onStorageSelected();
            } else if (id == R.id.menu_trash) {
                if (handler != null) handler.onTrashSelected();
            } else if (id == R.id.menu_bookmarks) {
                if (handler != null) handler.onBookmarksSelected();
            } else if (id == R.id.menu_settings) {
                if (handler != null) handler.onSettingsSelected();
            }
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            // Drawer items are stateful actions, not persistent selections — keep them unchecked
            // so the next open doesn't show a stale highlight.
            item.setChecked(false);
            return true;
        });
    }

    @Nullable
    private DrawerActionHandler currentDrawerHandler() {
        Fragment hosted = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        return hosted instanceof DrawerActionHandler ? (DrawerActionHandler) hosted : null;
    }

    private void applyDarkChromeSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    // ---------- Permission gate (previously PermissionGateActivity) ----------

    private boolean hasAllFilesAccess() {
        return Environment.isExternalStorageManager();
    }

    private void requestAllFilesAccess() {
        Intent specific = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:" + getPackageName()));
        if (tryLaunchSettings(specific)) {
            return;
        }
        Intent generic = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        if (tryLaunchSettings(generic)) {
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

    /**
     * Settings callback. On success, install the real content inline (no {@code recreate()} —
     * recreate has been observed to race with the launcher dispatch on MIUI and leave the activity
     * in the empty-window "black screen" state). On denial, finish cleanly.
     */
    private void resolvePermissionAndContinue() {
        if (hasAllFilesAccess()) {
            installContent();
        } else {
            finish();
        }
    }
}

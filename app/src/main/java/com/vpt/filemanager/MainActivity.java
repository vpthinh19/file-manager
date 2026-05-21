package com.vpt.filemanager;

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

import com.vpt.filemanager.util.StorageScope;
import com.vpt.filemanager.node.FilePath;
import com.vpt.filemanager.browser.DualPaneHostFragment;

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
 * <p>Phase R-7b: Trash + Bookmark giờ render qua pane (xem [[project-dom-virtual-concept]]) — drawer
 * routing đẩy active pane navigate tới virtual root tương ứng, không còn fragment swap. Drawer
 * highlight detect qua scheme của active pane currentPath.
 *
 * <p>System bars stay dark (chrome) in both light and dark themes — see
 * {@link #applyDarkChromeSystemBars()}.
 */
@AndroidEntryPoint
public final class MainActivity extends AppCompatActivity implements DrawerHost, DrawerActionHandler {
    private static final String TAG_DUAL_PANE = "dual-pane";

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

    /**
     * Drawer routing lives at the Activity layer — the Activity owns the {@code fragment_container}
     * and is therefore the right level to decide which navigation to trigger. Phase R-7b: thay vì
     * swap fragment, ta đẩy active pane navigate tới virtual root (TRASH_ROOT / BOOKMARK_ROOT) hoặc
     * storage root.
     */
    private void wireDrawerNavigation() {
        NavigationView navView = findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_storage) {
                onStorageSelected();
            } else if (id == R.id.menu_trash) {
                onTrashSelected();
            } else if (id == R.id.menu_bookmarks) {
                onBookmarksSelected();
            }
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            // syncDrawerSelection() được DualPaneHostFragment kích hoạt sau khi pane đổi path
            // — không setChecked tay tránh race với observer-driven highlight.
            return true;
        });
    }

    /**
     * Highlight drawer entry theo scheme của active pane. Gọi bởi {@link DualPaneHostFragment} khi
     * active pane đổi path hoặc khi user swap pane left↔right; cũng được gọi 1 lần ngay sau
     * {@link #installContent()} để cover cold-start.
     */
    @Override
    public void syncDrawerSelection() {
        NavigationView navView = findViewById(R.id.nav_view);
        if (navView == null) {
            return;
        }
        navView.setCheckedItem(activeDrawerItemId());
    }

    private int activeDrawerItemId() {
        Fragment hosted = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (hosted instanceof DualPaneHostFragment dual) {
            FilePath path = dual.activeVm().currentPath();
            if (path != null) {
                if (path.isTrash()) {
                    return R.id.menu_trash;
                }
                if (path.isBookmark()) {
                    return R.id.menu_bookmarks;
                }
            }
        }
        return R.id.menu_storage;
    }

    // ---------- DrawerActionHandler ----------

    @Override
    public void onStorageSelected() {
        navigateActiveIfHosted(StorageScope.rootPath());
    }

    @Override
    public void onTrashSelected() {
        navigateActiveIfHosted(FilePath.TRASH_ROOT);
    }

    @Override
    public void onBookmarksSelected() {
        navigateActiveIfHosted(FilePath.BOOKMARK_ROOT);
    }

    private void navigateActiveIfHosted(FilePath target) {
        Fragment hosted = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (hosted instanceof DualPaneHostFragment dual) {
            // Idempotent: tránh navigate-no-op khi user re-tap drawer cùng item (cách khác là check
            // path.equals(target) nhưng PaneViewModel.navigateTo đã guard nội bộ).
            dual.navigateActivePaneTo(target);
        }
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

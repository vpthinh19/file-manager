package com.vpt.filemanager.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
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
import com.vpt.filemanager.ui.drawer.DrawerActionHandler;
import com.vpt.filemanager.ui.drawer.DrawerHost;
import com.vpt.filemanager.ui.dualpane.DualPaneHostFragment;

/**
 * Hosts {@link DualPaneHostFragment} plus the navigation drawer. System bars match the
 * dark chrome color ({@code md_chrome_bg}) in both light and dark themes (MT-faithful), so the
 * bar icons must always be light — we never want dark-on-dark.
 *
 * <p>Drawer plumbing lives here so Fragments only need to talk to two narrow interfaces —
 * {@link DrawerHost} (open the drawer) and {@link DrawerActionHandler} (react to nav items).
 */
@AndroidEntryPoint
public final class MainActivity extends AppCompatActivity implements DrawerHost {
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyDarkChromeSystemBars();
        drawerLayout = findViewById(R.id.drawer_layout);
        wireDrawerNavigation();
        if (savedInstanceState == null) {
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
}

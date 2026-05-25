package com.vpt.filemanager.ui.main;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.threading.AppExecutors;
import com.vpt.filemanager.operation.FileOperations;
import com.vpt.filemanager.settings.UserPreferences;
import com.vpt.filemanager.ui.bottombar.BottomBarComponent;
import com.vpt.filemanager.ui.content.ContentHostComponent;
import com.vpt.filemanager.ui.drawer.DrawerComponent;
import com.vpt.filemanager.ui.pane.PaneId;
import com.vpt.filemanager.ui.pane.PaneFragment;
import com.vpt.filemanager.ui.state.StateViewModel;
import com.vpt.filemanager.ui.topbar.TopBarComponent;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** Android host only: permission, component installation and system Back boundary. */
@AndroidEntryPoint
public final class MainActivity extends AppCompatActivity {
    @Inject UserPreferences preferences;
    @Inject FileOperations operations;
    @Inject AppExecutors executors;
    private StateViewModel state;
    private DrawerComponent drawer;
    private ContentHostComponent content;
    private boolean installed;
    private final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> resumeAfterPermission());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Boolean dark = preferences.darkThemeOverride();
        if (dark != null) {
            AppCompatDelegate.setDefaultNightMode(dark
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        }
        EdgeToEdge.enable(this);
        if (Environment.isExternalStorageManager()) install(savedInstanceState);
        else requestPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!installed && Environment.isExternalStorageManager()) install(null);
    }

    private void install(@Nullable Bundle savedInstanceState) {
        if (installed) return;
        installed = true;
        setContentView(R.layout.activity_main);
        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        state = new ViewModelProvider(this).get(StateViewModel.class);
        drawer = new DrawerComponent(this, state, preferences);
        content = new ContentHostComponent(this, state, drawer);
        drawer.attach(this);
        new TopBarComponent(this, state, operations, executors, drawer).attach(this);
        new BottomBarComponent(this, state, operations, executors).attach(this);
        content.attach(this);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.pane_left_container, PaneFragment.newInstance(PaneId.LEFT), "pane-left")
                    .replace(R.id.pane_right_container, PaneFragment.newInstance(PaneId.RIGHT), "pane-right")
                    .commitNow();
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (content.onBackPressed()) return;
                if (drawer.closeIfOpen()) return;
                if (state.activeState().selectionMode) {
                    state.clearSelection(state.activePaneValue(), true);
                } else if (!state.back(state.activePaneValue())) {
                    finish();
                }
            }
        });
    }

    private void requestPermission() {
        Intent request = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            settingsLauncher.launch(request);
        } catch (RuntimeException unavailable) {
            try {
                settingsLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (RuntimeException noSettings) {
                Toast.makeText(this, "All-files access is unavailable", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void resumeAfterPermission() {
        if (Environment.isExternalStorageManager()) install(null); else finish();
    }
}

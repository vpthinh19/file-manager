package com.vpt.filemanager.component.main;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.threading.AppExecutors;
import com.vpt.filemanager.storage.facade.StorageFacade;
import com.vpt.filemanager.core.settings.UserPreferences;
import com.vpt.filemanager.component.bottombar.BottomBarComponent;
import com.vpt.filemanager.component.content.ContentHostComponent;
import com.vpt.filemanager.component.drawer.DrawerComponent;
import com.vpt.filemanager.component.pane.PaneId;
import com.vpt.filemanager.component.pane.PaneFragment;
import com.vpt.filemanager.component.state.StateViewModel;
import com.vpt.filemanager.component.topbar.TopBarComponent;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** Android host only: permission, component installation and system Back boundary. */
@AndroidEntryPoint
public final class MainActivity extends AppCompatActivity {
    @Inject UserPreferences preferences;
    @Inject StorageFacade storage;
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
        installInsets();
        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        state = new ViewModelProvider(this).get(StateViewModel.class);
        drawer = new DrawerComponent(this, state, preferences);
        content = new ContentHostComponent(this, state, drawer);
        drawer.attach(this);
        new TopBarComponent(this, state, storage, executors, drawer).attach(this);
        new BottomBarComponent(this, state, storage, executors).attach(this);
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

    private void installInsets() {
        View appBar = findViewById(R.id.appbar);
        View bottom = findViewById(R.id.bottom_container);
        int appLeft = appBar.getPaddingLeft();
        int appTop = appBar.getPaddingTop();
        int appRight = appBar.getPaddingRight();
        int appBottom = appBar.getPaddingBottom();
        int bottomLeft = bottom.getPaddingLeft();
        int bottomTop = bottom.getPaddingTop();
        int bottomRight = bottom.getPaddingRight();
        int bottomPadding = bottom.getPaddingBottom();
        int baseBottomHeight = bottom.getLayoutParams().height;
        View root = findViewById(R.id.drawer_layout);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBar.setPadding(appLeft, appTop + bars.top, appRight, appBottom);
            bottom.setPadding(bottomLeft, bottomTop, bottomRight, bottomPadding + bars.bottom);
            ViewGroup.LayoutParams parameters = bottom.getLayoutParams();
            parameters.height = baseBottomHeight + bars.bottom;
            bottom.setLayoutParams(parameters);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}

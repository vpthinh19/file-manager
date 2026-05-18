package com.vpt.filemanager.ui;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.ui.dualpane.DualPaneHostFragment;

/**
 * Hosts the {@link DualPaneHostFragment}. The status / navigation bar colors come from the theme
 * ({@code Theme.FileManager}); this Activity only flips the system-bar icon contrast so dark icons
 * appear on a light background and vice versa.
 */
@AndroidEntryPoint
public final class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarIconContrast();
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new DualPaneHostFragment())
                    .commit();
        }
    }

    private void applySystemBarIconContrast() {
        boolean isLight = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(isLight);
        controller.setAppearanceLightNavigationBars(isLight);
    }
}

package com.vpt.filemanager.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.ui.dualpane.DualPaneHostFragment;

/**
 * Hosts the {@link DualPaneHostFragment}. System bars match the dark chrome color
 * ({@code md_chrome_bg}) in both light and dark themes (MT-faithful), so the bar icons must
 * always be light — we never want dark-on-dark.
 */
@AndroidEntryPoint
public final class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyDarkChromeSystemBars();
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new DualPaneHostFragment())
                    .commit();
        }
    }

    private void applyDarkChromeSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }
}

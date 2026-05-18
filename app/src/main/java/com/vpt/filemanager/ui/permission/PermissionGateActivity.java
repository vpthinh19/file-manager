package com.vpt.filemanager.ui.permission;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.ui.MainActivity;

/**
 * One-shot bootstrap activity that ensures {@link Environment#isExternalStorageManager()} before
 * MainActivity is shown.
 *
 * <p>Flow:
 * <ol>
 *   <li>If permission is already granted &rarr; launch MainActivity and finish.</li>
 *   <li>Otherwise, launch the system Settings page via {@link ActivityResultLauncher}. Using the
 *       launcher (instead of {@code startActivity}) keeps the result tied to this activity so when
 *       the user presses back from Settings, control returns here — not to the Launcher.</li>
 *   <li>On the result callback we re-check permission and either open MainActivity or finish.</li>
 * </ol>
 *
 * <p>The activity uses {@code Theme.FileManager.Translucent} so the user never sees an empty page.
 */
@AndroidEntryPoint
public final class PermissionGateActivity extends AppCompatActivity {

    private final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> proceed());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            proceed();
        }
    }

    /**
     * Single decision point: either open MainActivity, launch Settings, or finish. Idempotent —
     * safe to call multiple times; only acts when state actually needs to advance.
     */
    private void proceed() {
        if (hasStorageAccess()) {
            openMain();
        } else {
            launchManageStorageSettings();
        }
    }

    private boolean hasStorageAccess() {
        return Environment.isExternalStorageManager();
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void launchManageStorageSettings() {
        Intent specific = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:" + getPackageName()));
        if (tryLaunch(specific)) {
            return;
        }
        Intent generic = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        if (tryLaunch(generic)) {
            return;
        }
        Toast.makeText(this,
                "All-files access settings not available on this device", Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean tryLaunch(Intent intent) {
        try {
            settingsLauncher.launch(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}

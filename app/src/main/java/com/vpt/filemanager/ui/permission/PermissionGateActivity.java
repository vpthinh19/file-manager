package com.vpt.filemanager.ui.permission;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.ui.MainActivity;

@AndroidEntryPoint
public final class PermissionGateActivity extends AppCompatActivity {
    private static final String STATE_SETTINGS_OPENED = "settings_opened";

    private boolean settingsOpened;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            settingsOpened = savedInstanceState.getBoolean(STATE_SETTINGS_OPENED, false);
        }
        if (hasStorageAccess()) {
            openMain();
            return;
        }
        if (!settingsOpened) {
            openManageStorageSettings();
            settingsOpened = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStorageAccess()) {
            openMain();
        } else if (settingsOpened) {
            // User returned without granting; close the gate gracefully.
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SETTINGS_OPENED, settingsOpened);
    }

    private boolean hasStorageAccess() {
        return Environment.isExternalStorageManager();
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void openManageStorageSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception fallback) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }
}

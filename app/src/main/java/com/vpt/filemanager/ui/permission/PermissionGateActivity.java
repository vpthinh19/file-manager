package com.vpt.filemanager.ui.permission;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.ui.MainActivity;

@AndroidEntryPoint
public final class PermissionGateActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (hasStorageAccess()) {
            openMain();
        } else {
            showRationale();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStorageAccess()) {
            openMain();
        }
    }

    private boolean hasStorageAccess() {
        return Environment.isExternalStorageManager();
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showRationale() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(R.string.permission_title);
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText(R.string.permission_body);
        body.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        body.setPadding(0, padding / 2, 0, padding / 2);
        root.addView(body);

        MaterialButton settings = new MaterialButton(this);
        settings.setText(R.string.permission_open_settings);
        settings.setOnClickListener(v -> openManageStorageSettings());
        root.addView(settings);

        setContentView(root);
    }

    private void openManageStorageSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}


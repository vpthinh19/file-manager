package com.vpt.filemanager.media.image;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.vpt.filemanager.R;

import java.io.File;

/** Full-screen image surface; bitmap allocation and request lifecycle are owned by Glide. */
public final class ImageViewerActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "com.vpt.filemanager.extra.IMAGE_PATH";

    private ImageView image;
    private RequestManager requests;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_image_viewer);
        applyInsets();
        MaterialToolbar toolbar = findViewById(R.id.image_toolbar);
        toolbar.setTitle(new File(path).getName());
        toolbar.setNavigationOnClickListener(view -> finish());
        image = findViewById(R.id.image_content);
        requests = Glide.with(this);
        requests.load(new File(path)).fitCenter().into(image);
    }

    private void applyInsets() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        View root = findViewById(R.id.image_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        if (image != null && requests != null) {
            requests.clear(image);
        }
        super.onDestroy();
    }
}

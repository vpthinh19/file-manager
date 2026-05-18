package com.vpt.filemanager.ui.viewer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.util.ThemeUtils;

/**
 * Lightweight wrapper around sora-editor's {@code CodeEditor}.
 *
 * <p>Chrome (root background, header bar, title, save action) is themed via M3 attrs and adapts to
 * light/dark automatically. The editor surface itself currently uses sora's built-in {@code
 * SchemeDarcula}; Phase 3 will plug in a theme-aware sora color scheme.
 */
public final class TextEditorActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "com.vpt.filemanager.extra.PATH";

    private static final int EDITOR_READ_ONLY_THRESHOLD = 1024 * 1024;

    private Path path;
    private CodeEditor editor;
    private TextView save;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBarIconContrast();
        path = Path.of(getIntent().getStringExtra(EXTRA_PATH));
        buildUi();
        load();
    }

    private void applySystemBarIconContrast() {
        boolean isLight = ThemeUtils.isLightTheme(this);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(isLight);
        controller.setAppearanceLightNavigationBars(isLight);
    }

    private void buildUi() {
        int colorSurface = ThemeUtils.color(this, com.google.android.material.R.attr.colorSurface);
        int colorSurfaceContainer = ThemeUtils.color(
                this, com.google.android.material.R.attr.colorSurfaceContainer);
        int colorOnSurface = ThemeUtils.color(this, com.google.android.material.R.attr.colorOnSurface);
        int colorPrimary = ThemeUtils.color(this, androidx.appcompat.R.attr.colorPrimary);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorSurface);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(24), dp(12), dp(8), dp(12));
        header.setBackgroundColor(colorSurfaceContainer);

        TextView title = new TextView(this);
        title.setText(path.toString());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setTextColor(colorOnSurface);
        title.setTextSize(16);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(title);

        save = new TextView(this);
        save.setText(R.string.action_save);
        save.setTextColor(colorPrimary);
        save.setTextSize(16);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(16), dp(8), dp(8), dp(8));
        save.setOnClickListener(v -> save());
        header.addView(save);
        root.addView(header);

        editor = new CodeEditor(this);
        editor.setTextSize(14);
        editor.setColorScheme(new SchemeDarcula());
        editor.setEditorLanguage(new EmptyLanguage());
        editor.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(editor);
        setContentView(root);
    }

    private void load() {
        try {
            long size = Files.size(path);
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            if (size > EDITOR_READ_ONLY_THRESHOLD) {
                editor.setText(content.substring(0,
                        Math.min(content.length(), EDITOR_READ_ONLY_THRESHOLD)));
                lockEditor("Large file opened read-only");
                return;
            }
            editor.setText(content);
            boolean writable = Files.isWritable(path);
            editor.setEditable(writable);
            save.setEnabled(writable);
            if (!writable) {
                save.setText(R.string.read_only);
            }
        } catch (IOException | SecurityException e) {
            lockEditor(null);
            editor.setText(e.getMessage() == null ? "Error reading file" : e.getMessage());
        }
    }

    private void lockEditor(@Nullable String userMessage) {
        editor.setEditable(false);
        save.setEnabled(false);
        save.setText(R.string.read_only);
        if (userMessage != null) {
            toast(userMessage);
        }
    }

    private void save() {
        try {
            String text = editor.getText().toString();
            Files.write(path, text.getBytes(StandardCharsets.UTF_8));
            toast("Saved");
        } catch (IOException | SecurityException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

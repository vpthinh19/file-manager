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
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;

import com.vpt.filemanager.R;

public final class TextEditorActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "com.vpt.filemanager.extra.PATH";

    private Path path;
    private CodeEditor editor;
    private TextView save;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF050505);
        path = Path.of(getIntent().getStringExtra(EXTRA_PATH));
        buildUi();
        load();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF202020);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(24, 16, 16, 16);
        header.setBackgroundColor(0xFF050505);

        TextView title = new TextView(this);
        title.setText(path.toString());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setTextColor(0xFFE0E0E0);
        title.setTextSize(16);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(title);

        save = new TextView(this);
        save.setText(R.string.action_save);
        save.setTextColor(0xFF03A9F4);
        save.setTextSize(16);
        save.setGravity(Gravity.CENTER);
        save.setPadding(24, 8, 8, 8);
        save.setOnClickListener(v -> save());
        header.addView(save);
        root.addView(header);

        editor = new CodeEditor(this);
        editor.setBackgroundColor(0xFF1E1E1E);
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
            if (size > 1024 * 1024) {
                editor.setText(content.substring(0, Math.min(content.length(), 1024 * 1024)));
                editor.setEditable(false);
                save.setEnabled(false);
                save.setText(R.string.read_only);
                toast("Large file opened read-only");
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
            editor.setEditable(false);
            save.setEnabled(false);
            save.setText(R.string.read_only);
            editor.setText(e.getMessage() == null ? "Error reading file" : e.getMessage());
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

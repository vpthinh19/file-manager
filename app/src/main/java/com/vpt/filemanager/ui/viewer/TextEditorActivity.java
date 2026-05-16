package com.vpt.filemanager.ui.viewer;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.vpt.filemanager.R;

public final class TextEditorActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "com.vpt.filemanager.extra.PATH";

    private Path path;
    private EditText editor;
    private TextView save;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        path = Path.of(getIntent().getStringExtra(EXTRA_PATH));
        buildUi();
        load();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF202020);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(16, 16, 16, 12);
        header.setBackgroundColor(0xFF050505);

        TextView title = new TextView(this);
        title.setText(path.toString());
        title.setSingleLine(true);
        title.setTextColor(0xFFE0E0E0);
        title.setTextSize(18);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(title);

        save = new TextView(this);
        save.setText(R.string.action_save);
        save.setTextColor(0xFF03A9F4);
        save.setTextSize(16);
        save.setGravity(Gravity.CENTER);
        save.setPadding(24, 12, 8, 12);
        save.setOnClickListener(v -> save());
        header.addView(save);
        root.addView(header);

        editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextColor(0xFFE0E0E0);
        editor.setHintTextColor(0xFF9E9E9E);
        editor.setTextSize(14);
        editor.setTypeface(android.graphics.Typeface.MONOSPACE);
        editor.setBackgroundColor(0xFF202020);
        editor.setPadding(16, 16, 16, 16);
        editor.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1));
        root.addView(editor);
        setContentView(root);
    }

    private void load() {
        try {
            long size = Files.size(path);
            if (size > 1024 * 1024) {
                String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                editor.setText(content.substring(0, Math.min(content.length(), 1024 * 1024)));
                editor.setEnabled(false);
                save.setEnabled(false);
                save.setText(R.string.read_only);
                toast("Large file opened read-only");
                return;
            }
            editor.setText(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            boolean writable = Files.isWritable(path);
            editor.setEnabled(writable);
            save.setEnabled(writable);
            if (!writable) {
                save.setText(R.string.read_only);
            }
        } catch (IOException | SecurityException e) {
            editor.setEnabled(false);
            save.setEnabled(false);
            save.setText(R.string.read_only);
            editor.setText(e.getMessage());
        }
    }

    private void save() {
        try {
            Files.write(path, editor.getText().toString().getBytes(StandardCharsets.UTF_8));
            toast("Saved");
        } catch (IOException | SecurityException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

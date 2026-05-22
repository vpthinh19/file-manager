package com.vpt.filemanager.editor;

import android.app.AlertDialog;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;

import com.vpt.filemanager.R;
import com.vpt.filemanager.event.FileTreeChangeBus;
import com.vpt.filemanager.format.ThemeUtils;
import com.vpt.filemanager.util.ErrorPresenter;

/**
 * Lightweight wrapper around sora-editor's {@code CodeEditor}.
 *
 * <p>Load pipeline (Phase 2C-7 hardening) is a small state machine: size check → binary sniff →
 * confirm dialog if binary → stream-decode with REPLACE policy → guarded {@code setText}. Every
 * step funnels failures through {@link ErrorPresenter} so the UX matches the rest of the app.
 *
 * <p>Phase R-8: emit {@link FileTreeChangeBus} sau khi save thành công để pane nào đang xem
 * folder cha của file này tự reload (size/mtime cập nhật). Activity scope ngoài Fragment host
 * nên không thể "tell pane refresh" trực tiếp — bus singleton là cầu nối duy nhất.
 */
@AndroidEntryPoint
public final class TextEditorActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "com.vpt.filemanager.extra.PATH";

    private static final long EDITOR_HARD_LIMIT = 8L * 1024 * 1024;   // > this = bail, too risky
    private static final long EDITOR_READ_ONLY_THRESHOLD = 1024 * 1024;
    private static final int SNIFF_BYTES = 4096;
    private static final int NULL_SCAN_WINDOW = 512;

    @Inject
    FileTreeChangeBus changeBus;

    private Path path;
    private CodeEditor editor;
    private TextView save;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBarIconContrast();
        path = Paths.get(getIntent().getStringExtra(EXTRA_PATH));
        buildUi();
        startLoad();
    }

    private void applySystemBarIconContrast() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    private void buildUi() {
        int colorSurface = ThemeUtils.color(this, com.google.android.material.R.attr.colorSurface);
        int chromeBg = getColor(R.color.md_chrome_bg);
        int chromeOn = getColor(R.color.md_chrome_on_bg);
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
        header.setBackgroundColor(chromeBg);

        TextView title = new TextView(this);
        title.setText(path.toString());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setTextColor(chromeOn);
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
        applySyntaxStyling();
        editor.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(editor);
        setContentView(root);
    }

    /**
     * Phase R-9: setup TextMate grammar + theme cho editor.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>{@link SyntaxSetup#ensureInitialized} idempotent — load grammars + theme lần đầu</li>
     *   <li>{@link LanguageResolver#scopeFor} → scope name nullable. Null → fallback Empty + dark</li>
     *   <li>Match scope → {@link TextMateLanguage#create} + {@link TextMateColorScheme}</li>
     * </ol>
     *
     * <p>Lỗi setup (asset thiếu, OOM...) graceful fallback {@link EmptyLanguage} +
     * {@link SchemeDarcula} — editor vẫn mở được dù không có syntax highlight.
     */
    private void applySyntaxStyling() {
        String scope = LanguageResolver.scopeFor(
                path.getFileName() == null ? "" : path.getFileName().toString());
        try {
            SyntaxSetup.ensureInitialized(this);
            if (scope != null) {
                editor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()));
                editor.setEditorLanguage(TextMateLanguage.create(scope, true));
                return;
            }
        } catch (Exception e) {
            // Asset/grammar load fail (asset corrupt / grammar không parse được / theme JSON
            // malformed) — fallback plain. KHÔNG bắt Throwable: OutOfMemoryError + linkage error
            // phải bubble up để crash report bắt được, fallback ở đây sẽ alloc thêm object →
            // mask root cause. Note: TextMateColorScheme.create() throws checked Exception.
            timber.log.Timber.w(e, "Syntax setup failed, fallback EmptyLanguage");
        }
        editor.setColorScheme(new SchemeDarcula());
        editor.setEditorLanguage(new EmptyLanguage());
    }

    /**
     * Pre-flight before reading: hard size cap + binary sniff. If the content is binary we ask
     * the user before continuing — this is the path that previously crashed when a user renamed
     * {@code .zip} → {@code .txt} and tried to open it.
     */
    private void startLoad() {
        long size;
        try {
            size = Files.size(path);
        } catch (IOException | SecurityException e) {
            ErrorPresenter.toast(this, e);
            finish();
            return;
        }
        if (size > EDITOR_HARD_LIMIT) {
            ErrorPresenter.toast(this, new IOException(getString(R.string.error_file_too_large)));
            finish();
            return;
        }
        boolean binary;
        try {
            binary = looksBinary(path);
        } catch (IOException | SecurityException e) {
            ErrorPresenter.toast(this, e);
            finish();
            return;
        }
        if (binary) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_binary_title)
                    .setMessage(getString(R.string.dialog_binary_message,
                            path.getFileName() == null ? path.toString()
                                    : path.getFileName().toString()))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                    .setOnCancelListener(d -> finish())
                    .setPositiveButton(R.string.dialog_open_anyway,
                            (d, w) -> doLoad(size))
                    .show();
        } else {
            doLoad(size);
        }
    }

    /**
     * Read the file as UTF-8 with a REPLACE-on-malformed decoder so binary or non-UTF-8 bytes turn
     * into {@code �} instead of throwing. {@code setText} is wrapped in a Throwable guard
     * because sora's text-actions pipeline has occasionally crashed on degenerate input.
     */
    private void doLoad(long size) {
        try {
            String content = readDecoded(path);
            if (size > EDITOR_READ_ONLY_THRESHOLD) {
                editor.setText(content.substring(0,
                        Math.min(content.length(), (int) EDITOR_READ_ONLY_THRESHOLD)));
                lockEditor(getString(R.string.editor_size_limit_read_only));
                return;
            }
            editor.setText(content);
            boolean writable = Files.isWritable(path);
            editor.setEditable(writable);
            save.setEnabled(writable);
            if (!writable) {
                save.setText(R.string.read_only);
            }
        } catch (Throwable t) {
            ErrorPresenter.toast(this, t);
            finish();
        }
    }

    /**
     * Scan up to {@link #SNIFF_BYTES} from the file's prefix; a null byte within the first
     * {@link #NULL_SCAN_WINDOW} bytes is a strong binary signal (UTF-8 text never contains
     * {@code 0x00}). Cheap heuristic — false positives for some Windows UTF-16 files, but the user
     * can still opt in via the confirm dialog.
     */
    private static boolean looksBinary(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[SNIFF_BYTES];
            int read = in.read(buf);
            int scanEnd = Math.min(read, NULL_SCAN_WINDOW);
            for (int i = 0; i < scanEnd; i++) {
                if (buf[i] == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String readDecoded(Path path) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buf = new char[4096];
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(
                        Files.newInputStream(path),
                        StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPLACE)
                                .onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
            int n;
            while ((n = reader.read(buf)) >= 0) {
                out.append(buf, 0, n);
            }
        }
        return out.toString();
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
            changeBus.emit();
        } catch (IOException | SecurityException e) {
            ErrorPresenter.toast(this, e);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

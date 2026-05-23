package com.vpt.filemanager.ui.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.vpt.filemanager.R;
import com.vpt.filemanager.error.ErrorPresenter;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.threading.AppExecutors;
import com.vpt.filemanager.workspace.MutationResult;
import com.vpt.filemanager.workspace.WorkspaceStore;

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
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.PublishSearchResultEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;

/**
 * In-app text editor surface backed by sora-editor.
 *
 * <p>All file and TextMate preparation work runs outside the main thread. The activity owns only
 * the editor view and one document language instance; {@link CodeEditor#release()} tears both down
 * when the view lifecycle ends while the process-wide grammar cache remains reusable.
 */
@AndroidEntryPoint
public final class TextEditorActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "com.vpt.filemanager.extra.PATH";

    private static final long EDITOR_HARD_LIMIT = 8L * 1024 * 1024;
    private static final int EDITOR_READ_ONLY_THRESHOLD = 1024 * 1024;
    private static final int SNIFF_BYTES = 4096;
    private static final int NULL_SCAN_WINDOW = 512;

    @Inject
    WorkspaceStore workspace;
    @Inject
    AppExecutors executors;

    private Path path;
    private MaterialToolbar toolbar;
    private CodeEditor editor;
    private ProgressBar progress;
    private TextView languageStatus;
    private TextView cursorStatus;
    private TextView modeStatus;
    private View searchBar;
    private EditText searchInput;
    private TextView searchResult;
    private ImageButton searchPrevious;
    private ImageButton searchNext;
    private MenuItem saveAction;
    private MenuItem undoAction;
    private MenuItem redoAction;
    private MenuItem searchAction;

    private boolean destroyed;
    private boolean documentLoaded;
    private boolean writable;
    private boolean modified;
    private Content savedContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBarIconContrast();
        String rawPath = getIntent().getStringExtra(EXTRA_PATH);
        if (rawPath == null) {
            finish();
            return;
        }
        path = Paths.get(rawPath);
        buildUi();
        startSyntaxSetup();
        startLoad();
    }

    private void applySystemBarIconContrast() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    private void buildUi() {
        setContentView(R.layout.activity_text_editor);
        View root = findViewById(R.id.editor_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
            return insets;
        });

        toolbar = findViewById(R.id.editor_toolbar);
        editor = findViewById(R.id.editor);
        progress = findViewById(R.id.editor_progress);
        languageStatus = findViewById(R.id.editor_language);
        cursorStatus = findViewById(R.id.editor_cursor);
        modeStatus = findViewById(R.id.editor_mode);
        searchBar = findViewById(R.id.editor_search_bar);
        searchInput = findViewById(R.id.editor_search_input);
        searchResult = findViewById(R.id.editor_search_result);
        searchPrevious = findViewById(R.id.editor_search_previous);
        searchNext = findViewById(R.id.editor_search_next);

        toolbar.setTitle(fileName());
        toolbar.setSubtitle(path.toString());
        toolbar.setNavigationOnClickListener(view -> finish());
        toolbar.inflateMenu(R.menu.menu_text_editor);
        saveAction = toolbar.getMenu().findItem(R.id.editor_action_save);
        undoAction = toolbar.getMenu().findItem(R.id.editor_action_undo);
        redoAction = toolbar.getMenu().findItem(R.id.editor_action_redo);
        searchAction = toolbar.getMenu().findItem(R.id.editor_action_search);
        toolbar.setOnMenuItemClickListener(this::onToolbarAction);

        editor.setColorScheme(isNightMode() ? new SchemeDarcula() : new SchemeGitHub());
        editor.setEditorLanguage(new EmptyLanguage());
        editor.setTextSize(14);
        editor.setLineInfoTextSize(12);
        editor.setLineNumberEnabled(true);
        editor.setPinLineNumber(true);
        editor.setHighlightCurrentLine(true);
        editor.setHighlightBracketPair(true);
        editor.setBlockLineEnabled(true);
        editor.setScrollBarEnabled(true);
        editor.setTabWidth(4);
        editor.setLineSpacing(2f, 1.04f);

        editor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (!documentLoaded || event.getAction() == ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                return;
            }
            modified = savedContent == null || !editor.getText().equals(savedContent);
            updateActions();
        });
        editor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) ->
                renderCursor(event.getLeft()));
        editor.subscribeEvent(PublishSearchResultEvent.class, (event, unsubscribe) ->
                renderSearchResult());
        setupSearchBar();
        updateActions();
    }

    private boolean onToolbarAction(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.editor_action_save) {
            save();
            return true;
        }
        if (id == R.id.editor_action_search) {
            showSearchBar();
            return true;
        }
        if (id == R.id.editor_action_undo) {
            editor.undo();
            updateActions();
            return true;
        }
        if (id == R.id.editor_action_redo) {
            editor.redo();
            updateActions();
            return true;
        }
        return false;
    }

    private void startSyntaxSetup() {
        String scope = LanguageResolver.scopeFor(fileName());
        if (scope == null) {
            languageStatus.setText(R.string.editor_plain_text);
            return;
        }
        languageStatus.setText(getString(R.string.editor_syntax_loading, SyntaxCatalog.displayName(scope)));
        executors.computation().execute(() -> {
            try {
                TextMateLanguage language = SyntaxSetup.createLanguage(getApplicationContext(), scope);
                TextMateColorScheme colorScheme =
                        TextMateColorScheme.create(ThemeRegistry.getInstance());
                executors.main().execute(() -> applyPreparedSyntax(scope, language, colorScheme));
            } catch (Exception error) {
                timber.log.Timber.w(error, "Syntax setup failed for %s", scope);
                executors.main().execute(() -> {
                    if (!destroyed) {
                        languageStatus.setText(R.string.editor_plain_text);
                    }
                });
            }
        });
    }

    private void applyPreparedSyntax(
            String scope,
            @Nullable TextMateLanguage language,
            TextMateColorScheme colorScheme) {
        if (language == null) {
            languageStatus.setText(R.string.editor_plain_text);
            return;
        }
        if (destroyed || editor.isReleased()) {
            language.destroy();
            return;
        }
        editor.setColorScheme(colorScheme);
        editor.setEditorLanguage(language);
        languageStatus.setText(SyntaxCatalog.displayName(scope));
    }

    private void startLoad() {
        setLoading(true);
        executors.io().execute(() -> {
            try {
                long size = Files.size(path);
                if (size > EDITOR_HARD_LIMIT) {
                    throw new IOException(getString(R.string.error_file_too_large));
                }
                LoadProbe probe = new LoadProbe(size, looksBinary(path));
                executors.main().execute(() -> acceptProbe(probe));
            } catch (IOException | SecurityException error) {
                executors.main().execute(() -> failLoad(error));
            }
        });
    }

    private void acceptProbe(LoadProbe probe) {
        if (destroyed) {
            return;
        }
        if (probe.binary) {
            setLoading(false);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_binary_title)
                    .setMessage(getString(R.string.dialog_binary_message, fileName()))
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .setPositiveButton(R.string.dialog_open_anyway,
                            (dialog, which) -> readDocument(probe.size))
                    .show();
            return;
        }
        readDocument(probe.size);
    }

    private void readDocument(long size) {
        setLoading(true);
        executors.io().execute(() -> {
            try {
                boolean truncated = size > EDITOR_READ_ONLY_THRESHOLD;
                int charLimit = truncated ? EDITOR_READ_ONLY_THRESHOLD : Integer.MAX_VALUE;
                DocumentResult result = new DocumentResult(
                        readDecoded(path, charLimit),
                        !truncated && Files.isWritable(path),
                        truncated);
                executors.main().execute(() -> applyDocument(result));
            } catch (IOException | SecurityException error) {
                executors.main().execute(() -> failLoad(error));
            }
        });
    }

    private void applyDocument(DocumentResult result) {
        if (destroyed) {
            return;
        }
        try {
            documentLoaded = false;
            editor.setText(result.content);
            documentLoaded = true;
            writable = result.writable;
            savedContent = editor.getText().copyText();
            modified = false;
            editor.setEditable(writable);
            modeStatus.setText(writable ? R.string.editor_editable : R.string.read_only);
            renderCursor(editor.getCursor().left());
            updateActions();
            setLoading(false);
            if (result.truncated) {
                toast(getString(R.string.editor_size_limit_read_only));
            }
        } catch (RuntimeException error) {
            failLoad(error);
        }
    }

    private void failLoad(Throwable error) {
        if (destroyed) {
            return;
        }
        setLoading(false);
        ErrorPresenter.toast(this, error);
        finish();
    }

    private void save() {
        if (!writable || !modified) {
            return;
        }
        Content savedSnapshot = editor.getText().copyText();
        String text = savedSnapshot.toString();
        saveAction.setEnabled(false);
        executors.io().execute(() -> {
            try {
                Files.write(path, text.getBytes(StandardCharsets.UTF_8));
                executors.main().execute(() -> {
                    if (destroyed) {
                        return;
                    }
                    savedContent = savedSnapshot;
                    modified = !editor.getText().equals(savedContent);
                    updateActions();
                    toast(getString(R.string.editor_saved));
                    Path parent = path.getParent();
                    workspace.publish(parent == null
                            ? MutationResult.allLiveSnapshots()
                            : MutationResult.builder()
                                    .changedContainer(NodePath.local(parent.toString()))
                                    .build());
                });
            } catch (IOException | SecurityException error) {
                executors.main().execute(() -> {
                    if (!destroyed) {
                        updateActions();
                        ErrorPresenter.toast(this, error);
                    }
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void updateActions() {
        saveAction.setEnabled(documentLoaded && writable && modified);
        undoAction.setEnabled(documentLoaded && editor.canUndo());
        redoAction.setEnabled(documentLoaded && editor.canRedo());
        searchAction.setEnabled(documentLoaded);
        toolbar.setTitle(modified ? getString(R.string.editor_modified_title, fileName()) : fileName());
    }

    private void setupSearchBar() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                String query = text.toString();
                if (query.isEmpty()) {
                    editor.getSearcher().stopSearch();
                    renderSearchResult();
                    return;
                }
                editor.getSearcher().search(query, new EditorSearcher.SearchOptions(
                        EditorSearcher.SearchOptions.TYPE_NORMAL, true));
            }
        });
        searchPrevious.setOnClickListener(view -> {
            if (editor.getSearcher().gotoPrevious()) {
                renderSearchResult();
            }
        });
        searchNext.setOnClickListener(view -> {
            if (editor.getSearcher().gotoNext()) {
                renderSearchResult();
            }
        });
        findViewById(R.id.editor_search_close).setOnClickListener(view -> hideSearchBar());
    }

    private void showSearchBar() {
        searchBar.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideSearchBar() {
        editor.getSearcher().stopSearch();
        searchInput.setText("");
        searchBar.setVisibility(View.GONE);
        searchInput.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        renderSearchResult();
    }

    private void renderSearchResult() {
        EditorSearcher searcher = editor.getSearcher();
        int total = searcher.hasQuery() ? searcher.getMatchedPositionCount() : 0;
        int current = total == 0 ? 0 : searcher.getCurrentMatchedPositionIndex() + 1;
        searchResult.setText(getString(R.string.editor_search_result, current, total));
        boolean hasMatches = total > 0;
        searchPrevious.setEnabled(hasMatches);
        searchNext.setEnabled(hasMatches);
        searchPrevious.setAlpha(hasMatches ? 1f : 0.38f);
        searchNext.setAlpha(hasMatches ? 1f : 0.38f);
    }

    private void renderCursor(CharPosition position) {
        cursorStatus.setText(getString(R.string.editor_cursor_position,
                position.line + 1, position.column + 1));
    }

    private String fileName() {
        return path.getFileName() == null ? path.toString() : path.getFileName().toString();
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static boolean looksBinary(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[SNIFF_BYTES];
            int read = input.read(buffer);
            int scanEnd = Math.min(Math.max(read, 0), NULL_SCAN_WINDOW);
            for (int index = 0; index < scanEnd; index++) {
                if (buffer[index] == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String readDecoded(Path path, int charLimit) throws IOException {
        StringBuilder result = new StringBuilder(Math.min(charLimit, 16 * 1024));
        char[] buffer = new char[4096];
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                Files.newInputStream(path),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
            while (result.length() < charLimit) {
                int remaining = charLimit - result.length();
                int count = reader.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count < 0) {
                    break;
                }
                result.append(buffer, 0, count);
            }
        }
        return result.toString();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (editor != null && !editor.isReleased()) {
            editor.release();
        }
        super.onDestroy();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static final class LoadProbe {
        final long size;
        final boolean binary;

        LoadProbe(long size, boolean binary) {
            this.size = size;
            this.binary = binary;
        }
    }

    private static final class DocumentResult {
        final String content;
        final boolean writable;
        final boolean truncated;

        DocumentResult(String content, boolean writable, boolean truncated) {
            this.content = content;
            this.writable = writable;
            this.truncated = truncated;
        }
    }
}

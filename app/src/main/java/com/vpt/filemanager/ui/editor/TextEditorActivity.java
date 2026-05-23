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
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.threading.AppExecutors;
import com.vpt.filemanager.workspace.DocumentSession;
import com.vpt.filemanager.workspace.WorkspaceStore;

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
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;

/**
 * In-app text editor surface backed by sora-editor.
 *
 * <p>All document I/O and conflict state belongs to {@link DocumentSession}; all TextMate
 * preparation work runs outside the main thread. The activity owns only Android rendering and one
 * document language instance; {@link CodeEditor#release()} tears both down when the view lifecycle
 * ends while the process-wide grammar cache remains reusable.
 */
@AndroidEntryPoint
public final class TextEditorActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "com.vpt.filemanager.extra.PATH";

    @Inject
    WorkspaceStore workspace;
    @Inject
    AppExecutors executors;

    private NodePath path;
    private DocumentSession session;
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
    private SyntaxSetup.LanguageLease languageLease;

    private boolean destroyed;
    private boolean documentLoaded;
    private boolean writable;
    private boolean modified;
    private boolean externalConflict;
    private boolean externalAlertShown;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBarIconContrast();
        String rawPath = getIntent().getStringExtra(EXTRA_PATH);
        if (rawPath == null) {
            finish();
            return;
        }
        try {
            path = NodePath.parse(rawPath);
        } catch (IllegalArgumentException error) {
            finish();
            return;
        }
        session = workspace.openDocument(path);
        buildUi();
        session.externalInvalidations().observe(this, ignored -> checkExternalState());
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
        toolbar.setSubtitle(path.isLocal() ? path.path() : path.toString());
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
            modified = session.isDirty(editor.getText());
            updateActions();
        });
        editor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) ->
                renderCursor(event.getLeft()));
        editor.subscribeEvent(PublishSearchResultEvent.class, (event, unsubscribe) -> {
            EditorSearcher searcher = editor.getSearcher();
            if (searcher.hasQuery()
                    && searcher.getMatchedPositionCount() > 0
                    && searcher.getCurrentMatchedPositionIndex() < 0) {
                searcher.gotoNext();
            }
            renderSearchResult();
        });
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
            SyntaxSetup.LanguageLease preparedLease = null;
            try {
                preparedLease = SyntaxSetup.acquireLanguage(getApplicationContext(), scope);
                TextMateColorScheme colorScheme =
                        TextMateColorScheme.create(ThemeRegistry.getInstance());
                SyntaxSetup.LanguageLease lease = preparedLease;
                executors.main().execute(() -> applyPreparedSyntax(scope, lease, colorScheme));
            } catch (Exception error) {
                if (preparedLease != null) {
                    preparedLease.language().destroy();
                    preparedLease.close();
                }
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
            @Nullable SyntaxSetup.LanguageLease lease,
            TextMateColorScheme colorScheme) {
        if (lease == null) {
            languageStatus.setText(R.string.editor_plain_text);
            return;
        }
        TextMateLanguage language = lease.language();
        if (destroyed || editor.isReleased()) {
            language.destroy();
            lease.close();
            return;
        }
        languageLease = lease;
        editor.setColorScheme(colorScheme);
        editor.setEditorLanguage(language);
        languageStatus.setText(SyntaxCatalog.displayName(scope));
    }

    private void startLoad() {
        setLoading(true);
        executors.io().execute(() -> {
            try {
                DocumentSession.LoadResult result = session.load(false);
                executors.main().execute(() -> acceptLoadResult(result));
            } catch (NodeException | SecurityException error) {
                executors.main().execute(() -> failLoad(error));
            }
        });
    }

    private void acceptLoadResult(DocumentSession.LoadResult result) {
        if (destroyed) {
            return;
        }
        if (result.binaryApprovalRequired) {
            setLoading(false);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_binary_title)
                    .setMessage(getString(R.string.dialog_binary_message, fileName()))
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                    .setOnCancelListener(dialog -> finish())
                    .setPositiveButton(R.string.dialog_open_anyway,
                            (dialog, which) -> loadDocument(true))
                    .show();
            return;
        }
        applyDocument(result);
    }

    private void loadDocument(boolean allowBinary) {
        setLoading(true);
        executors.io().execute(() -> {
            try {
                DocumentSession.LoadResult result = session.load(allowBinary);
                executors.main().execute(() -> applyDocument(result));
            } catch (NodeException | SecurityException error) {
                executors.main().execute(() -> failLoad(error));
            }
        });
    }

    private void applyDocument(DocumentSession.LoadResult result) {
        if (destroyed) {
            return;
        }
        try {
            documentLoaded = false;
            editor.setText(result.content);
            documentLoaded = true;
            writable = result.writable;
            modified = false;
            externalConflict = false;
            externalAlertShown = false;
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
        if (!writable || !modified || externalConflict) {
            return;
        }
        String text = editor.getText().toString();
        saveAction.setEnabled(false);
        executors.io().execute(() -> {
            try {
                session.save(text);
                executors.main().execute(() -> {
                    if (destroyed) {
                        return;
                    }
                    modified = session.isDirty(editor.getText());
                    updateActions();
                    toast(getString(R.string.editor_saved));
                });
            } catch (DocumentSession.ConflictException conflict) {
                executors.main().execute(() -> handleExternalConflict(
                        DocumentSession.ExternalState.MODIFIED));
            } catch (NodeException | SecurityException error) {
                DocumentSession.ExternalState externalState = session.inspectExternalState();
                executors.main().execute(() -> {
                    if (!destroyed) {
                        if (externalState == DocumentSession.ExternalState.UNCHANGED) {
                            updateActions();
                            ErrorPresenter.toast(this, error);
                        } else {
                            handleExternalConflict(externalState);
                        }
                    }
                });
            }
        });
    }

    private void checkExternalState() {
        if (!documentLoaded || destroyed) {
            return;
        }
        executors.io().execute(() -> {
            DocumentSession.ExternalState state = session.inspectExternalState();
            executors.main().execute(() -> {
                if (!destroyed && state != DocumentSession.ExternalState.UNCHANGED) {
                    handleExternalConflict(state);
                }
            });
        });
    }

    private void handleExternalConflict(DocumentSession.ExternalState state) {
        if (state == DocumentSession.ExternalState.MODIFIED && !modified) {
            toast(getString(R.string.editor_external_reloading));
            loadDocument(true);
            return;
        }
        externalConflict = true;
        if (state == DocumentSession.ExternalState.DELETED) {
            writable = false;
            modeStatus.setText(R.string.editor_missing);
        } else {
            modeStatus.setText(R.string.editor_conflict);
        }
        updateActions();
        if (externalAlertShown) {
            return;
        }
        externalAlertShown = true;
        int message = state == DocumentSession.ExternalState.DELETED
                ? R.string.editor_deleted_external_message
                : R.string.editor_modified_external_message;
        new AlertDialog.Builder(this)
                .setTitle(R.string.editor_external_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void updateActions() {
        saveAction.setEnabled(documentLoaded && writable && modified && !externalConflict);
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
        return path.name();
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (session != null) {
            session.close();
        }
        if (editor != null && !editor.isReleased()) {
            editor.release();
        }
        if (languageLease != null) {
            languageLease.close();
            languageLease = null;
        }
        super.onDestroy();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}

package com.vpt.filemanager.ui.content.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.vpt.filemanager.R;
import com.vpt.filemanager.core.error.DocumentConflictException;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.threading.AppExecutors;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.storage.archive.ArchiveAccess;
import com.vpt.filemanager.ui.content.FullScreenContent;
import com.vpt.filemanager.ui.content.OpenedContent;
import com.vpt.filemanager.ui.state.StateViewModel;

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

/** Full-screen text component hosted inside MainActivity and driven by pane navigation state. */
@AndroidEntryPoint
public final class TextEditorFragment extends Fragment implements FullScreenContent {
    private static final String ARG_PATH = "path";
    private static final String ARG_NAME = "name";
    private static final String ARG_READ_ONLY = "readOnly";
    private static final String ARG_ARCHIVE = "archive";

    @Inject DocumentService documents;
    @Inject AppExecutors executors;
    @Inject ArchiveAccess archives;
    private DocumentSession session;
    private StateViewModel state;
    private String path;
    private String displayName;
    @Nullable private Path archiveEntry;
    private MaterialToolbar toolbar;
    private CodeEditor editor;
    private ProgressBar progress;
    private TextView languageStatus;
    private TextView cursorStatus;
    private TextView modeStatus;
    private View searchBar;
    private EditText searchInput;
    private TextView searchResult;
    private MenuItem saveAction;
    private MenuItem undoAction;
    private MenuItem redoAction;
    private MenuItem searchAction;
    @Nullable private SyntaxSetup.LanguageLease languageLease;
    private boolean documentLoaded;
    private boolean writable;
    private boolean modified;
    private boolean destroyed;
    private boolean closeAfterSave;

    public static TextEditorFragment newInstance(OpenedContent content) {
        TextEditorFragment fragment = new TextEditorFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_PATH, content.localPath());
        arguments.putString(ARG_NAME, content.displayName());
        arguments.putBoolean(ARG_READ_ONLY, content.readOnly());
        if (content.archiveEntry() != null) {
            arguments.putString(ARG_ARCHIVE, content.archiveEntry().serialize());
        }
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                                       @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.content_text_editor, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        state = new ViewModelProvider(requireActivity()).get(StateViewModel.class);
        path = requireArguments().getString(ARG_PATH);
        displayName = requireArguments().getString(ARG_NAME);
        String serialized = requireArguments().getString(ARG_ARCHIVE);
        archiveEntry = serialized == null ? null : Path.parse(serialized);
        session = documents.open(path);
        toolbar = view.findViewById(R.id.editor_toolbar);
        editor = view.findViewById(R.id.editor);
        progress = view.findViewById(R.id.editor_progress);
        languageStatus = view.findViewById(R.id.editor_language);
        cursorStatus = view.findViewById(R.id.editor_cursor);
        modeStatus = view.findViewById(R.id.editor_mode);
        searchBar = view.findViewById(R.id.editor_search_bar);
        searchInput = view.findViewById(R.id.editor_search_input);
        searchResult = view.findViewById(R.id.editor_search_result);
        configureToolbar(view);
        configureEditor(view);
        session.externalInvalidations().observe(getViewLifecycleOwner(), ignored -> checkExternalState());
        startSyntax();
        load(false);
    }

    private void configureToolbar(View view) {
        toolbar.setTitle(displayName);
        toolbar.setSubtitle(path);
        toolbar.setNavigationOnClickListener(ignored -> onBackPressed());
        toolbar.inflateMenu(R.menu.menu_text_editor);
        saveAction = toolbar.getMenu().findItem(R.id.editor_action_save);
        undoAction = toolbar.getMenu().findItem(R.id.editor_action_undo);
        redoAction = toolbar.getMenu().findItem(R.id.editor_action_redo);
        searchAction = toolbar.getMenu().findItem(R.id.editor_action_search);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.editor_action_save) save(false);
            else if (item.getItemId() == R.id.editor_action_undo) editor.undo();
            else if (item.getItemId() == R.id.editor_action_redo) editor.redo();
            else if (item.getItemId() == R.id.editor_action_search) showSearch();
            else return false;
            updateActions();
            return true;
        });
        ImageButton previous = view.findViewById(R.id.editor_search_previous);
        ImageButton next = view.findViewById(R.id.editor_search_next);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                if (text.length() == 0) editor.getSearcher().stopSearch();
                else editor.getSearcher().search(text.toString(),
                        new EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true));
                renderSearch(previous, next);
            }
        });
        previous.setOnClickListener(ignored -> {
            editor.getSearcher().gotoPrevious();
            renderSearch(previous, next);
        });
        next.setOnClickListener(ignored -> {
            editor.getSearcher().gotoNext();
            renderSearch(previous, next);
        });
        view.findViewById(R.id.editor_search_close).setOnClickListener(ignored -> hideSearch());
    }

    private void configureEditor(View view) {
        editor.setColorScheme(isNight() ? new SchemeDarcula() : new SchemeGitHub());
        editor.setEditorLanguage(new EmptyLanguage());
        editor.setTextSize(14);
        editor.setLineNumberEnabled(true);
        editor.setPinLineNumber(true);
        editor.setHighlightCurrentLine(true);
        editor.setHighlightBracketPair(true);
        editor.setTabWidth(4);
        editor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (documentLoaded && event.getAction() != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                modified = session.isDirty(editor.getText());
                updateActions();
            }
        });
        editor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) ->
                renderCursor(event.getLeft()));
        editor.subscribeEvent(PublishSearchResultEvent.class, (event, unsubscribe) -> {
            ImageButton previous = view.findViewById(R.id.editor_search_previous);
            ImageButton next = view.findViewById(R.id.editor_search_next);
            renderSearch(previous, next);
        });
        updateActions();
    }

    private void startSyntax() {
        String scope = LanguageResolver.scopeFor(displayName);
        if (scope == null) return;
        languageStatus.setText(getString(R.string.editor_syntax_loading, SyntaxCatalog.displayName(scope)));
        executors.computation().execute(() -> {
            try {
                SyntaxSetup.LanguageLease lease = SyntaxSetup.acquireLanguage(requireContext().getApplicationContext(), scope);
                TextMateColorScheme scheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
                executors.main().execute(() -> {
                    if (destroyed || editor.isReleased()) {
                        lease.language().destroy();
                        lease.close();
                        return;
                    }
                    languageLease = lease;
                    TextMateLanguage language = lease.language();
                    editor.setColorScheme(scheme);
                    editor.setEditorLanguage(language);
                    languageStatus.setText(SyntaxCatalog.displayName(scope));
                });
            } catch (Exception error) {
                executors.main().execute(() -> languageStatus.setText(R.string.editor_plain_text));
            }
        });
    }

    private void load(boolean allowBinary) {
        progress.setVisibility(View.VISIBLE);
        executors.io().execute(() -> {
            try {
                DocumentSession.LoadResult result = session.load(allowBinary);
                executors.main().execute(() -> accept(result));
            } catch (Exception error) {
                executors.main().execute(() -> fail(error));
            }
        });
    }

    private void accept(DocumentSession.LoadResult result) {
        progress.setVisibility(View.GONE);
        if (result.binaryApprovalRequired) {
            new AlertDialog.Builder(requireContext()).setTitle(R.string.dialog_binary_title)
                    .setMessage(getString(R.string.dialog_binary_message, displayName))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> close())
                    .setPositiveButton(R.string.dialog_open_anyway, (d, w) -> load(true)).show();
            return;
        }
        documentLoaded = false;
        editor.setText(result.content);
        documentLoaded = true;
        writable = result.writable && !requireArguments().getBoolean(ARG_READ_ONLY);
        modified = false;
        editor.setEditable(writable);
        modeStatus.setText(writable ? R.string.editor_editable : R.string.read_only);
        updateActions();
    }

    private void save(boolean close) {
        if (!writable || !modified) {
            if (close) close();
            return;
        }
        closeAfterSave = close;
        String content = editor.getText().toString();
        saveAction.setEnabled(false);
        executors.io().execute(() -> {
            try {
                session.save(content);
                if (archiveEntry != null) archives.updateFromMaterialized(archiveEntry, path);
                executors.main().execute(() -> {
                    modified = session.isDirty(editor.getText());
                    updateActions();
                    state.refreshVisiblePanes();
                    if (closeAfterSave) close(); else toast(getString(R.string.editor_saved));
                });
            } catch (DocumentConflictException conflict) {
                executors.main().execute(() -> toast(conflict.getMessage()));
            } catch (FileOperationException error) {
                executors.main().execute(() -> toast(error.getMessage()));
            }
        });
    }

    private void checkExternalState() {
        if (!documentLoaded) return;
        executors.io().execute(() -> {
            DocumentSession.ExternalState external = session.inspectExternalState();
            if (external != DocumentSession.ExternalState.UNCHANGED) {
                executors.main().execute(() -> {
                    writable = false;
                    modeStatus.setText(external == DocumentSession.ExternalState.DELETED
                            ? R.string.editor_missing : R.string.editor_conflict);
                    updateActions();
                });
            }
        });
    }

    private void updateActions() {
        if (saveAction == null) return;
        saveAction.setEnabled(documentLoaded && writable && modified);
        undoAction.setEnabled(documentLoaded && editor.canUndo());
        redoAction.setEnabled(documentLoaded && editor.canRedo());
        searchAction.setEnabled(documentLoaded);
        toolbar.setTitle(modified ? getString(R.string.editor_modified_title, displayName) : displayName);
    }

    private void showSearch() {
        searchBar.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        ((InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideSearch() {
        editor.getSearcher().stopSearch();
        searchInput.setText("");
        searchBar.setVisibility(View.GONE);
    }

    private void renderSearch(ImageButton previous, ImageButton next) {
        EditorSearcher searcher = editor.getSearcher();
        int count = searcher.hasQuery() ? searcher.getMatchedPositionCount() : 0;
        int current = count == 0 ? 0 : searcher.getCurrentMatchedPositionIndex() + 1;
        searchResult.setText(getString(R.string.editor_search_result, current, count));
        previous.setEnabled(count > 0);
        next.setEnabled(count > 0);
    }

    private void renderCursor(CharPosition position) {
        cursorStatus.setText(getString(R.string.editor_cursor_position,
                position.line + 1, position.column + 1));
    }

    private void fail(Throwable error) {
        progress.setVisibility(View.GONE);
        toast(error.getMessage());
        close();
    }

    @Override public boolean onBackPressed() {
        if (!modified) {
            close();
        } else {
            new AlertDialog.Builder(requireContext()).setTitle(displayName)
                    .setMessage(R.string.editor_unsaved_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.editor_discard, (dialog, which) -> close())
                    .setPositiveButton(R.string.action_save, (dialog, which) -> save(true)).show();
        }
        return true;
    }

    private void close() {
        state.back(state.activePaneValue());
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void toast(@Nullable String text) {
        Toast.makeText(requireContext(), text == null ? getString(R.string.error_unknown) : text,
                Toast.LENGTH_SHORT).show();
    }

    @Override public void onDestroyView() {
        destroyed = true;
        if (session != null) session.close();
        if (editor != null && !editor.isReleased()) editor.release();
        if (languageLease != null) languageLease.close();
        super.onDestroyView();
    }
}

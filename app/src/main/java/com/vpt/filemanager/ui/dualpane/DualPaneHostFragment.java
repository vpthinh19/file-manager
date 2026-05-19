package com.vpt.filemanager.ui.dualpane;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.vpt.filemanager.ui.common.NameDeconflict;
import java.io.File;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.core.io.FileOpener;
import com.vpt.filemanager.core.storage.StorageScope;
import com.vpt.filemanager.core.util.ByteSize;
import com.vpt.filemanager.core.util.MimeTypes;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.domain.model.FileCategory;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.ui.browser.NodeActionsBottomSheet;
import com.vpt.filemanager.ui.browser.OpenAsDialogFragment;
import com.vpt.filemanager.ui.browser.PaneFragment;
import com.vpt.filemanager.ui.browser.PaneViewModel;
import com.vpt.filemanager.ui.drawer.DrawerActionHandler;
import com.vpt.filemanager.ui.drawer.DrawerHost;
import com.vpt.filemanager.ui.properties.PropertiesDialogFragment;
import com.vpt.filemanager.ui.viewer.TextEditorActivity;

@AndroidEntryPoint
public final class DualPaneHostFragment extends Fragment implements PaneController, DrawerActionHandler {
    public static final String PANE_LEFT = "left";
    public static final String PANE_RIGHT = "right";

    private static final String TAG_PANE_LEFT = "pane_left";
    private static final String TAG_PANE_RIGHT = "pane_right";
    private static final String STATE_ACTIVE_PANE = "active_pane";

    private FragmentDualPaneHostBinding binding;
    private PaneViewModel leftVm;
    private PaneViewModel rightVm;
    private String activePaneId = PANE_LEFT;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewModelProvider provider = new ViewModelProvider(this);
        leftVm = provider.get(PANE_LEFT, PaneViewModel.class);
        rightVm = provider.get(PANE_RIGHT, PaneViewModel.class);
        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString(STATE_ACTIVE_PANE);
            if (saved != null) {
                activePaneId = saved;
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_ACTIVE_PANE, activePaneId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dual_pane_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentDualPaneHostBinding.bind(view);

        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.pane_left_container, PaneFragment.newInstance(PANE_LEFT), TAG_PANE_LEFT)
                    .replace(R.id.pane_right_container, PaneFragment.newInstance(PANE_RIGHT), TAG_PANE_RIGHT)
                    .commitNow();
        }

        configureToolbar();
        configureBottomBar();
        configureSelectionBar();
        applyInsets();
        installBackHandler();

        observePane(PANE_LEFT, leftVm);
        observePane(PANE_RIGHT, rightVm);

        applyActivePaneVisual();
        syncFromActive();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ---------- PaneController ----------

    @NonNull
    @Override
    public PaneViewModel viewModelForPane(@NonNull String paneId) {
        return PANE_RIGHT.equals(paneId) ? rightVm : leftVm;
    }

    @Override
    public void onPaneActivated(@NonNull String paneId) {
        if (paneId.equals(activePaneId)) {
            return;
        }
        activePaneId = paneId;
        applyActivePaneVisual();
        syncFromActive();
    }

    @Override
    public void onOpenFile(@NonNull String paneId, @NonNull FileNode node) {
        if (!paneId.equals(activePaneId)) {
            activePaneId = paneId;
            applyActivePaneVisual();
            syncFromActive();
        }
        PaneViewModel vm = viewModelForPane(paneId);

        if (!node.isDirectory()
                && FileCategory.ofExtension(node.name()) == FileCategory.UNKNOWN) {
            showOpenAsDialog(node);
            return;
        }

        FileOpener.Action action = FileOpener.decide(node);
        switch (action) {
            case OPEN_TEXT:
                openAsText(node.path());
                break;
            case OPEN_ARCHIVE:
                if (node.path().isLocal()) {
                    vm.openArchive(node.path());
                } else {
                    toast("Nested archive: coming in Phase 2C");
                }
                break;
            case OPEN_IMAGE:
            case OPEN_VIDEO:
            case OPEN_AUDIO:
            case OPEN_WITH:
            default:
                if (node.path().isLocal()) {
                    openWith(node);
                } else {
                    toast("Opening files inside archive: coming in Phase 2C");
                }
                break;
        }
    }

    private void showOpenAsDialog(@NonNull FileNode node) {
        if (!node.path().isLocal()) {
            toast("Opening files inside archive: coming in Phase 2C");
            return;
        }
        OpenAsDialogFragment.newInstance(node.name())
                .setListener(choice -> handleOpenAs(node, choice))
                .show(getChildFragmentManager(), "open-as");
    }

    private void handleOpenAs(@NonNull FileNode node, @NonNull OpenAsDialogFragment.OpenAs choice) {
        switch (choice) {
            case TEXT:
                openAsText(node.path());
                break;
            case IMAGE:
                openWithMime(node.path(), "image/*");
                break;
            case VIDEO:
                openWithMime(node.path(), "video/*");
                break;
            case AUDIO:
                openWithMime(node.path(), "audio/*");
                break;
            case ARCHIVE:
                activeVm().openArchive(node.path());
                break;
        }
    }

    private void openAsText(@NonNull FilePath path) {
        if (!path.isLocal()) {
            toast("Editing inside archive: coming in Phase 2C");
            return;
        }
        Intent intent = new Intent(requireContext(), TextEditorActivity.class);
        intent.putExtra(TextEditorActivity.EXTRA_PATH, path.path());
        startActivity(intent);
    }

    // ---------- Setup ----------

    private void configureToolbar() {
        MaterialToolbar toolbar = binding.toolbar;
        toolbar.setNavigationOnClickListener(v -> {
            if (requireActivity() instanceof DrawerHost host) {
                host.openDrawer();
            }
        });
    }

    // ---------- DrawerActionHandler ----------

    @Override
    public void onStorageSelected() {
        activeVm().navigateTo(StorageScope.rootPath());
    }

    @Override
    public void onTrashSelected() {
        activeVm().openTrash();
    }

    @Override
    public void onBookmarksSelected() {
        toast(getString(R.string.coming_soon));
    }

    @Override
    public void onSettingsSelected() {
        toast(getString(R.string.coming_soon));
    }

    private void configureBottomBar() {
        binding.btnBack.setOnClickListener(v -> activeVm().back());
        binding.btnForward.setOnClickListener(v -> activeVm().forward());
        binding.btnUp.setOnClickListener(v -> activeVm().navigateUp());
        binding.btnAdd.setOnClickListener(v -> showCreateDialog());
        binding.btnSwap.setOnClickListener(v -> onPaneActivated(
                PANE_LEFT.equals(activePaneId) ? PANE_RIGHT : PANE_LEFT));
    }

    private static final float DISABLED_ALPHA = 0.38f;

    private void applyNavButtonState(boolean canBack, boolean canForward) {
        binding.btnBack.setEnabled(canBack);
        binding.btnBack.setAlpha(canBack ? 1f : DISABLED_ALPHA);
        binding.btnForward.setEnabled(canForward);
        binding.btnForward.setAlpha(canForward ? 1f : DISABLED_ALPHA);
    }

    private void configureSelectionBar() {
        binding.btnSelCancel.setOnClickListener(v -> activeVm().clearSelection());
        binding.btnSelAll.setOnClickListener(v -> activeVm().selectAllVisible());
        binding.btnSelMore.setOnClickListener(v -> showSelectionMoreSheet());
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomContainer, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            return insets;
        });
    }

    private void installBackHandler() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        // Drawer wins: a visible drawer should always close on back, regardless
                        // of pane / selection state.
                        if (requireActivity() instanceof DrawerHost host && host.isDrawerOpen()) {
                            host.closeDrawer();
                            return;
                        }
                        PaneViewModel vm = activeVm();
                        if (vm.isInSelectionMode()) {
                            vm.clearSelection();
                            return;
                        }
                        if (!vm.navigateUp()) {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }

    // ---------- Observers ----------

    private void observePane(@NonNull String paneId, @NonNull PaneViewModel vm) {
        vm.uiState().observe(getViewLifecycleOwner(), state -> {
            if (paneId.equals(activePaneId) && !vm.isInSelectionMode()) {
                renderToolbarForState(state);
            }
        });
        vm.selection().observe(getViewLifecycleOwner(), selection -> {
            if (!paneId.equals(activePaneId)) {
                return;
            }
            renderBottomBars(selection);
            // Leaving selection mode must restore the path-derived toolbar title; otherwise the
            // "N selected" string from renderBottomBars sticks even after the selection clears.
            if (selection == null || selection.isEmpty()) {
                renderToolbarForState(vm.uiState().getValue());
            }
        });
        vm.canGoBack().observe(getViewLifecycleOwner(), can -> {
            if (paneId.equals(activePaneId)) {
                applyNavButtonState(Boolean.TRUE.equals(can),
                        Boolean.TRUE.equals(vm.canGoForward().getValue()));
            }
        });
        vm.canGoForward().observe(getViewLifecycleOwner(), can -> {
            if (paneId.equals(activePaneId)) {
                applyNavButtonState(Boolean.TRUE.equals(vm.canGoBack().getValue()),
                        Boolean.TRUE.equals(can));
            }
        });
        vm.events().observe(getViewLifecycleOwner(), this::toast);
    }

    private void syncFromActive() {
        PaneViewModel vm = activeVm();
        Set<FilePath> selection = vm.selection().getValue();
        if (selection != null && !selection.isEmpty()) {
            renderBottomBars(selection);
        } else {
            renderBottomBars(null);
            renderToolbarForState(vm.uiState().getValue());
        }
        applyNavButtonState(
                Boolean.TRUE.equals(vm.canGoBack().getValue()),
                Boolean.TRUE.equals(vm.canGoForward().getValue()));
    }

    private void renderToolbarForState(@Nullable PaneViewModel.UiState state) {
        if (state instanceof PaneViewModel.UiState.Content content) {
            setTitle(displayPath(content.path));
            if (content.path.isArchive()) {
                setSubtitle(getString(R.string.stats_archive,
                        content.folderCount + content.fileCount));
            } else if (content.totalBytes > 0) {
                setSubtitle(getString(R.string.stats_with_disk,
                        content.folderCount, content.fileCount,
                        ByteSize.format(content.freeBytes), ByteSize.format(content.totalBytes)));
            } else {
                setSubtitle(getString(R.string.stats_basic,
                        content.folderCount, content.fileCount));
            }
        } else if (state instanceof PaneViewModel.UiState.Roots roots) {
            setTitle(StorageScope.ROOT_PATH);
            setSubtitle(getString(R.string.stats_roots, roots.roots.size()));
        } else if (state instanceof PaneViewModel.UiState.Empty empty) {
            setTitle(displayPath(empty.path));
            setSubtitle(getString(R.string.stats_basic, 0, 0));
        } else if (state instanceof PaneViewModel.UiState.Error error) {
            setTitle(displayPath(error.path));
            setSubtitle(getString(R.string.error_listing_denied));
        } else {
            setTitle("");
            setSubtitle("");
        }
    }

    private void setTitle(@NonNull CharSequence text) {
        binding.tvToolbarTitle.setText(text);
    }

    private void setSubtitle(@Nullable CharSequence text) {
        binding.tvToolbarSubtitle.setText(text == null ? "" : text);
    }

    private void renderBottomBars(@Nullable Set<FilePath> selection) {
        boolean inMode = selection != null && !selection.isEmpty();
        binding.bottomBar.setVisibility(inMode ? View.GONE : View.VISIBLE);
        binding.selectionBar.setVisibility(inMode ? View.VISIBLE : View.GONE);
        if (inMode) {
            setTitle(getString(R.string.selected_count, selection.size()));
            setSubtitle("");
        }
    }

    // ---------- Visuals ----------

    private void applyActivePaneVisual() {
        PaneFragment left = (PaneFragment) getChildFragmentManager().findFragmentByTag(TAG_PANE_LEFT);
        PaneFragment right = (PaneFragment) getChildFragmentManager().findFragmentByTag(TAG_PANE_RIGHT);
        if (left != null) {
            left.setPaneActivated(PANE_LEFT.equals(activePaneId));
        }
        if (right != null) {
            right.setPaneActivated(PANE_RIGHT.equals(activePaneId));
        }
    }

    // ---------- Action menus ----------

    private void showCreateDialog() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_item, null, false);
        TextInputEditText nameField = view.findViewById(R.id.et_name);
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggle_type);
        // Default to Folder — MaterialButtonToggleGroup ignores XML "checked" on children, so we
        // wire the initial state here.
        toggle.check(R.id.btn_type_folder);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_create)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    String name = nameField.getText() == null
                            ? "" : nameField.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    boolean isFolder = toggle.getCheckedButtonId() == R.id.btn_type_folder;
                    attemptCreate(isFolder, name);
                })
                .show();
    }

    /**
     * Pre-check whether {@code name} already exists in the active pane's current dir. If clean,
     * fire the underlying use case. If it collides, show the Phase 2C-6 KISS conflict dialog
     * (Replace / Keep both / Cancel) so the user picks how to resolve before any FS mutation.
     */
    private void attemptCreate(boolean isFolder, @NonNull String name) {
        PaneViewModel vm = activeVm();
        FilePath current = vm.currentPath();
        if (current == null || !current.isLocal()) {
            return;
        }
        File target = new File(current.path(), name);
        if (!target.exists()) {
            if (isFolder) vm.createFolder(name); else vm.createFile(name);
            return;
        }
        showCreateConflictDialog(isFolder, name, current);
    }

    private void showCreateConflictDialog(boolean isFolder, @NonNull String name,
                                          @NonNull FilePath dir) {
        File dirFile = new File(dir.path());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.conflict_title)
                .setMessage(getString(R.string.conflict_message_format, name))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.conflict_keep_both, (d, w) -> {
                    String unique = NameDeconflict.unique(dirFile, name);
                    PaneViewModel vm = activeVm();
                    if (isFolder) vm.createFolder(unique); else vm.createFile(unique);
                })
                .setPositiveButton(R.string.conflict_replace, (d, w) -> {
                    activeVm().deleteThenCreate(dir.child(name), isFolder);
                })
                .show();
    }

    private void showSelectionMoreSheet() {
        PaneViewModel vm = activeVm();
        Set<FilePath> selection = vm.selection().getValue();
        if (selection == null || selection.isEmpty()) {
            return;
        }
        boolean single = selection.size() == 1;
        FilePath singlePath = single ? selection.iterator().next() : null;
        FileNode singleNode = single ? vm.findNode(singlePath) : null;

        NodeActionsBottomSheet sheet = NodeActionsBottomSheet
                .newInstance(single ? singlePath.name() : selection.size() + " items")
                .setDisabledActions(computeDisabledActions(selection, singleNode))
                .setListener(action -> handleSelectionAction(action, singlePath));
        sheet.show(getChildFragmentManager(), "selection-more");
    }

    /**
     * Decide which actions to grey out for the current selection. Rules:
     *   - multi-select disables single-target actions (rename, properties, open-with, bookmark)
     *   - single folder disables open-with (no external viewer for folders)
     *   - single file disables bookmark (v1 bookmarks are folders)
     *   - any archive entry disables write actions (archive support is read-only in v1)
     */
    private EnumSet<NodeActionsBottomSheet.Action> computeDisabledActions(
            @NonNull Set<FilePath> selection, @Nullable FileNode singleNode) {
        EnumSet<NodeActionsBottomSheet.Action> disabled =
                EnumSet.noneOf(NodeActionsBottomSheet.Action.class);
        boolean multi = selection.size() > 1;
        if (multi) {
            disabled.add(NodeActionsBottomSheet.Action.RENAME);
            disabled.add(NodeActionsBottomSheet.Action.PROPERTIES);
            disabled.add(NodeActionsBottomSheet.Action.OPEN_WITH);
            disabled.add(NodeActionsBottomSheet.Action.BOOKMARK);
        } else if (singleNode != null) {
            if (singleNode.isDirectory()) {
                disabled.add(NodeActionsBottomSheet.Action.OPEN_WITH);
            } else {
                disabled.add(NodeActionsBottomSheet.Action.BOOKMARK);
            }
        }
        for (FilePath p : selection) {
            if (p.isArchive()) {
                disabled.add(NodeActionsBottomSheet.Action.RENAME);
                disabled.add(NodeActionsBottomSheet.Action.DELETE);
                disabled.add(NodeActionsBottomSheet.Action.MOVE);
                disabled.add(NodeActionsBottomSheet.Action.COMPRESS);
                break;
            }
        }
        return disabled;
    }

    private void handleSelectionAction(NodeActionsBottomSheet.Action action, @Nullable FilePath singlePath) {
        PaneViewModel vm = activeVm();
        switch (action) {
            case DELETE:
                confirmDeleteSelected();
                break;
            case SHARE:
                shareSelected();
                break;
            case RENAME:
                if (singlePath == null) {
                    toast(getString(R.string.selection_single_only));
                } else {
                    final FilePath target = singlePath;
                    showNameDialog(R.string.action_rename, R.string.file_name,
                            newName -> vm.rename(target, newName));
                }
                break;
            case PROPERTIES:
                if (singlePath == null) {
                    toast(getString(R.string.selection_single_only));
                } else {
                    PropertiesDialogFragment.newInstance(singlePath.toString())
                            .show(getChildFragmentManager(), "properties");
                }
                break;
            case OPEN_WITH:
                if (singlePath == null) {
                    toast(getString(R.string.selection_single_only));
                } else if (singlePath.isLocal()) {
                    openWithPath(singlePath);
                }
                break;
            case COPY:
            case MOVE:
            case TOOLS:
            case COMPRESS:
            case BOOKMARK:
            default:
                toast(getString(action.labelRes) + " — coming in Phase 2C");
                break;
        }
    }

    private void confirmDeleteSelected() {
        PaneViewModel vm = activeVm();
        Set<FilePath> selection = vm.selection().getValue();
        if (selection == null || selection.isEmpty()) {
            return;
        }
        int count = selection.size();
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_delete)
                .setMessage(count == 1
                        ? selection.iterator().next().name()
                        : getString(R.string.delete_confirm_count, count))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> vm.deleteSelected())
                .show();
    }

    private void shareSelected() {
        PaneViewModel vm = activeVm();
        Set<FilePath> selection = vm.selection().getValue();
        if (selection == null || selection.isEmpty()) {
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        String authority = requireContext().getPackageName() + ".fileprovider";
        for (FilePath p : selection) {
            if (!p.isLocal()) {
                continue;
            }
            try {
                uris.add(FileProvider.getUriForFile(requireContext(), authority, new File(p.path())));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (uris.isEmpty()) {
            toast(getString(R.string.unavailable));
            return;
        }
        Intent intent;
        if (uris.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("*/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
            vm.clearSelection();
        } catch (ActivityNotFoundException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    private void showNameDialog(int titleRes, int hintRes, NameCallback callback) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_name_input, null, false);
        TextInputLayout til = view.findViewById(R.id.til_name);
        TextInputEditText input = view.findViewById(R.id.et_name);
        til.setHint(hintRes);
        new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save,
                        (dialog, which) -> callback.onName(
                                input.getText() == null ? "" : input.getText().toString()))
                .show();
    }

    private void openWith(@NonNull FileNode node) {
        openWithPath(node.path());
    }

    private void openWithPath(@NonNull FilePath path) {
        openWithMime(path, null);
    }

    /**
     * Launch the system "Open with" chooser. {@code mimeOverride} forces a MIME type (used by
     * {@link OpenAsDialogFragment} for extension-less files); pass {@code null} to auto-detect from
     * the file name.
     */
    private void openWithMime(@NonNull FilePath path, @Nullable String mimeOverride) {
        if (!path.isLocal()) {
            toast(getString(R.string.unavailable));
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    new File(path.path()));
            String mime = mimeOverride != null ? mimeOverride : MimeTypes.detect(path.name());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.action_open_with)));
        } catch (IllegalArgumentException | ActivityNotFoundException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    // ---------- Helpers ----------

    @NonNull
    private PaneViewModel activeVm() {
        return viewModelForPane(activePaneId);
    }

    /**
     * Absolute filesystem path shown in the toolbar. The toolbar's title TextView uses
     * {@code ellipsize="start"} + {@code singleLine="true"} so long paths get their leading
     * segments replaced with {@code "..."} while the meaningful end stays visible:
     * {@code /storage/emulated/0/Download/sub/file} →
     * {@code ...mulated/0/Download/sub/file} when the available width can't fit the full string.
     */
    @NonNull
    private static String displayPath(@NonNull FilePath path) {
        if (path.isArchive()) {
            FilePath archiveFile = FilePath.parse(path.authority());
            return archiveFile.path() + "!" + path.path();
        }
        return path.path();
    }

    private void toast(@NonNull CharSequence message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private interface NameCallback {
        void onName(String name);
    }
}

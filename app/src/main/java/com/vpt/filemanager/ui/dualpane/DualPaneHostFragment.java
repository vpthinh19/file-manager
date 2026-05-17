package com.vpt.filemanager.ui.dualpane;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

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
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.core.io.FileOpener;
import com.vpt.filemanager.core.util.ByteSize;
import com.vpt.filemanager.core.util.MimeTypes;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.ui.browser.NodeActionsBottomSheet;
import com.vpt.filemanager.ui.browser.PaneFragment;
import com.vpt.filemanager.ui.browser.PaneViewModel;
import com.vpt.filemanager.ui.properties.PropertiesDialogFragment;
import com.vpt.filemanager.ui.viewer.TextEditorActivity;

@AndroidEntryPoint
public final class DualPaneHostFragment extends Fragment implements PaneController {
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
        FileOpener.Action action = FileOpener.decide(node);
        switch (action) {
            case OPEN_TEXT:
                if (node.path().isLocal()) {
                    Intent intent = new Intent(requireContext(), TextEditorActivity.class);
                    intent.putExtra(TextEditorActivity.EXTRA_PATH, node.path().path());
                    startActivity(intent);
                } else {
                    toast("Editing inside archive: coming in Phase 2C");
                }
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

    // ---------- Setup ----------

    private void configureToolbar() {
        MaterialToolbar toolbar = binding.toolbar;
        toolbar.setNavigationOnClickListener(v -> toast("Drawer coming soon"));
    }

    private void configureBottomBar() {
        binding.btnUp.setOnClickListener(v -> activeVm().navigateUp());
        binding.btnAdd.setOnClickListener(v -> showCreateDialog());
        binding.btnSwap.setOnClickListener(v -> onPaneActivated(
                PANE_LEFT.equals(activePaneId) ? PANE_RIGHT : PANE_LEFT));
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
            if (paneId.equals(activePaneId)) {
                renderBottomBars(selection);
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
    }

    private void renderToolbarForState(@Nullable PaneViewModel.UiState state) {
        if (state instanceof PaneViewModel.UiState.Content content) {
            binding.toolbar.setTitle(displayPath(content.path));
            if (content.path.isArchive()) {
                binding.toolbar.setSubtitle(getString(R.string.stats_archive,
                        content.folderCount + content.fileCount));
            } else if (content.totalBytes > 0) {
                binding.toolbar.setSubtitle(getString(R.string.stats_with_disk,
                        content.folderCount, content.fileCount,
                        ByteSize.format(content.freeBytes), ByteSize.format(content.totalBytes)));
            } else {
                binding.toolbar.setSubtitle(getString(R.string.stats_basic,
                        content.folderCount, content.fileCount));
            }
        } else if (state instanceof PaneViewModel.UiState.Roots roots) {
            binding.toolbar.setTitle("/");
            binding.toolbar.setSubtitle(getString(R.string.stats_roots, roots.roots.size()));
        } else if (state instanceof PaneViewModel.UiState.Empty empty) {
            binding.toolbar.setTitle(displayPath(empty.path));
            binding.toolbar.setSubtitle(getString(R.string.stats_basic, 0, 0));
        } else if (state instanceof PaneViewModel.UiState.Error error) {
            binding.toolbar.setTitle(displayPath(error.path));
            binding.toolbar.setSubtitle(R.string.error_listing_denied);
        } else {
            binding.toolbar.setTitle(" ");
            binding.toolbar.setSubtitle(null);
        }
    }

    private void renderBottomBars(@Nullable Set<FilePath> selection) {
        boolean inMode = selection != null && !selection.isEmpty();
        binding.bottomBar.setVisibility(inMode ? View.GONE : View.VISIBLE);
        binding.selectionBar.setVisibility(inMode ? View.VISIBLE : View.GONE);
        if (inMode) {
            binding.toolbar.setTitle(getString(R.string.selected_count, selection.size()));
            binding.toolbar.setSubtitle(null);
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
        PaneViewModel vm = activeVm();
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_item, null, false);
        EditText nameField = view.findViewById(R.id.et_name);
        RadioButton rbFolder = view.findViewById(R.id.rb_folder);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_create)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    String name = nameField.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    if (rbFolder.isChecked()) {
                        vm.createFolder(name);
                    } else {
                        vm.createFile(name);
                    }
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
        FileNode singleNode = single ? null /* lookup in adapter only inside Pane; for now defer rename to use VM */ : null;

        NodeActionsBottomSheet sheet = NodeActionsBottomSheet
                .newInstance(single ? singlePath.name() : selection.size() + " items");
        sheet.setListener(action -> handleSelectionAction(action, singlePath));
        sheet.show(getChildFragmentManager(), "selection-more");
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
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint(hintRes);
        new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_create,
                        (dialog, which) -> callback.onName(input.getText().toString()))
                .show();
    }

    private void openWith(@NonNull FileNode node) {
        openWithPath(node.path());
    }

    private void openWithPath(@NonNull FilePath path) {
        if (!path.isLocal()) {
            toast(getString(R.string.unavailable));
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    new File(path.path()));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, MimeTypes.detect(path.name()));
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

    private static String displayPath(@NonNull FilePath path) {
        if (path.isArchive()) {
            FilePath archiveFile = FilePath.parse(path.authority());
            return archiveFile.path() + "!" + path.path();
        }
        return "/".equals(path.path()) ? "/" : path.path() + "/";
    }

    private void toast(@NonNull CharSequence message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private interface NameCallback {
        void onName(String name);
    }
}

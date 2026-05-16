package com.vpt.filemanager.ui.browser;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.core.io.FileOpener;
import com.vpt.filemanager.core.util.ByteSize;
import com.vpt.filemanager.core.util.MimeTypes;
import com.vpt.filemanager.databinding.FragmentFileBrowserBinding;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.ui.common.BaseFragment;
import com.vpt.filemanager.ui.properties.PropertiesDialogFragment;
import com.vpt.filemanager.ui.viewer.TextEditorActivity;

@AndroidEntryPoint
public final class FileBrowserFragment extends BaseFragment implements FileListAdapter.Listener {
    private static final int PANE_LEFT = 0;
    private static final int PANE_RIGHT = 1;

    private FragmentFileBrowserBinding binding;
    private FileBrowserViewModel leftViewModel;
    private FileBrowserViewModel rightViewModel;
    private FileListAdapter leftAdapter;
    private FileListAdapter rightAdapter;
    private int activePane = PANE_LEFT;
    private boolean dualPane;

    public FileBrowserFragment() {
        super(R.layout.fragment_file_browser);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentFileBrowserBinding.bind(view);
        dualPane = getResources().getBoolean(R.bool.dual_pane_enabled);

        leftViewModel = new ViewModelProvider(this).get("left", FileBrowserViewModel.class);
        leftAdapter = new FileListAdapter(PANE_LEFT, this);
        binding.rvLeft.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLeft.setAdapter(leftAdapter);

        if (dualPane) {
            rightViewModel = new ViewModelProvider(this).get("right", FileBrowserViewModel.class);
            rightAdapter = new FileListAdapter(PANE_RIGHT, this);
            binding.rvRight.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.rvRight.setAdapter(rightAdapter);
            binding.rvRight.setVisibility(View.VISIBLE);
            binding.dividerPanes.setVisibility(View.VISIBLE);
            binding.btnSwap.setVisibility(View.VISIBLE);
            observePane(PANE_RIGHT, rightViewModel, rightAdapter);
        } else {
            binding.btnSwap.setVisibility(View.GONE);
        }

        configureToolbar();
        configureBottomBar();
        configureSelectionBar();
        observePane(PANE_LEFT, leftViewModel, leftAdapter);
        applyInsets();
        installBackHandler();
        applyActivePaneVisual();

        if (savedInstanceState == null) {
            String home = Environment.getExternalStorageDirectory() != null
                    ? Environment.getExternalStorageDirectory().getAbsolutePath()
                    : "/storage/emulated/0";
            if (leftViewModel.currentPath() == null) {
                leftViewModel.navigateTo(FilePath.local(home));
            }
            if (dualPane && rightViewModel.currentPath() == null) {
                rightViewModel.navigateTo(FilePath.local(home));
            }
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onFileClicked(int pane, FileNode node) {
        setActivePane(pane);
        FileBrowserViewModel vm = viewModelFor(pane);
        if (vm.isInSelectionMode()) {
            vm.toggleSelect(node);
            return;
        }
        if (node instanceof ParentFileNode) {
            vm.navigateTo(node.path());
            return;
        }
        if (node.isDirectory()) {
            vm.onItemClicked(node);
        } else {
            openFile(pane, node);
        }
    }

    @Override
    public void onFileLongClicked(int pane, FileNode node) {
        setActivePane(pane);
        if (node instanceof ParentFileNode) {
            return;
        }
        viewModelFor(pane).toggleSelect(node);
    }

    private void configureToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> toast("Drawer coming soon"));
    }

    private void configureBottomBar() {
        binding.btnUp.setOnClickListener(v -> viewModelFor(activePane).navigateUp());
        binding.btnAdd.setOnClickListener(v -> showCreateMenu(binding.btnAdd));
        binding.btnTrash.setOnClickListener(v -> viewModelFor(activePane).openTrash());
        binding.btnSwap.setOnClickListener(v -> setActivePane(activePane == PANE_LEFT ? PANE_RIGHT : PANE_LEFT));
    }

    private void configureSelectionBar() {
        binding.btnSelCancel.setOnClickListener(v -> viewModelFor(activePane).clearSelection());
        binding.btnSelAll.setOnClickListener(v -> viewModelFor(activePane).selectAllVisible());
        binding.btnSelDelete.setOnClickListener(v -> confirmDeleteSelected());
        binding.btnSelShare.setOnClickListener(v -> shareSelected());
        binding.btnSelMore.setOnClickListener(v -> showSelectionMoreMenu());
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomContainer, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            return insets;
        });
    }

    private void observePane(int pane, FileBrowserViewModel viewModel, FileListAdapter adapter) {
        viewModel.uiState().observe(getViewLifecycleOwner(), state -> {
            boolean isActive = (pane == activePane);
            if (isActive) {
                binding.progress.setVisibility(state instanceof FileBrowserViewModel.UiState.Loading ? View.VISIBLE : View.GONE);
                binding.tvEmpty.setVisibility(state instanceof FileBrowserViewModel.UiState.Empty ? View.VISIBLE : View.GONE);
            }
            if (state instanceof FileBrowserViewModel.UiState.Content content) {
                adapter.submitList(withParent(content.path, content.nodes));
                if (isActive && !viewModel.isInSelectionMode()) {
                    updateToolbarForContent(content);
                }
            } else if (state instanceof FileBrowserViewModel.UiState.Roots roots) {
                adapter.submitList(new ArrayList<>(roots.roots));
                if (isActive && !viewModel.isInSelectionMode()) {
                    updateToolbarForRoots(roots);
                }
            } else if (state instanceof FileBrowserViewModel.UiState.Empty empty) {
                adapter.submitList(withParent(empty.path, List.of()));
                if (isActive && !viewModel.isInSelectionMode()) {
                    binding.toolbar.setTitle(displayPath(empty.path));
                    binding.toolbar.setSubtitle(getString(R.string.stats_basic, 0, 0));
                }
            } else if (state instanceof FileBrowserViewModel.UiState.Error error) {
                adapter.submitList(withParent(error.path, List.of()));
                if (isActive && !viewModel.isInSelectionMode()) {
                    binding.toolbar.setTitle(displayPath(error.path));
                    binding.toolbar.setSubtitle(R.string.error_listing_denied);
                    if (error.message != null) {
                        toast(error.message);
                    }
                }
            }
        });
        viewModel.selection().observe(getViewLifecycleOwner(), selection -> {
            adapter.setSelection(selection);
            if (pane == activePane) {
                refreshBottomBars(selection);
            }
        });
        viewModel.events().observe(getViewLifecycleOwner(), this::toast);
    }

    private void installBackHandler() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FileBrowserViewModel vm = viewModelFor(activePane);
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

    private void setActivePane(int pane) {
        if (activePane == pane) {
            return;
        }
        activePane = pane;
        applyActivePaneVisual();
        FileBrowserViewModel vm = viewModelFor(pane);
        if (vm.isInSelectionMode()) {
            refreshBottomBars(vm.selection().getValue());
        } else {
            refreshBottomBars(Collections.emptySet());
            FileBrowserViewModel.UiState state = vm.uiState().getValue();
            if (state instanceof FileBrowserViewModel.UiState.Content content) {
                updateToolbarForContent(content);
            } else if (state instanceof FileBrowserViewModel.UiState.Roots roots) {
                updateToolbarForRoots(roots);
            } else if (state instanceof FileBrowserViewModel.UiState.Empty empty) {
                binding.toolbar.setTitle(displayPath(empty.path));
                binding.toolbar.setSubtitle(getString(R.string.stats_basic, 0, 0));
            } else if (state instanceof FileBrowserViewModel.UiState.Error error) {
                binding.toolbar.setTitle(displayPath(error.path));
                binding.toolbar.setSubtitle(R.string.error_listing_denied);
            }
        }
    }

    private void applyActivePaneVisual() {
        if (!dualPane) {
            binding.rvLeft.setActivated(false);
            return;
        }
        binding.rvLeft.setActivated(activePane == PANE_LEFT);
        binding.rvRight.setActivated(activePane == PANE_RIGHT);
    }

    private void refreshBottomBars(Set<FilePath> selection) {
        boolean inMode = selection != null && !selection.isEmpty();
        binding.bottomBar.setVisibility(inMode ? View.GONE : View.VISIBLE);
        binding.selectionBar.setVisibility(inMode ? View.VISIBLE : View.GONE);
        if (inMode) {
            binding.toolbar.setTitle(getString(R.string.selected_count, selection.size()));
            binding.toolbar.setSubtitle(null);
        }
    }

    private void updateToolbarForContent(FileBrowserViewModel.UiState.Content content) {
        binding.toolbar.setTitle(displayPath(content.path));
        if (content.path.isArchive()) {
            binding.toolbar.setSubtitle(getString(R.string.stats_archive, content.folderCount + content.fileCount));
        } else if (content.totalBytes > 0) {
            binding.toolbar.setSubtitle(getString(R.string.stats_with_disk,
                    content.folderCount, content.fileCount,
                    ByteSize.format(content.freeBytes), ByteSize.format(content.totalBytes)));
        } else {
            binding.toolbar.setSubtitle(getString(R.string.stats_basic, content.folderCount, content.fileCount));
        }
    }

    private void updateToolbarForRoots(FileBrowserViewModel.UiState.Roots roots) {
        binding.toolbar.setTitle("/");
        binding.toolbar.setSubtitle(getString(R.string.stats_roots, roots.roots.size()));
    }

    private void showCreateMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.action_new_folder);
        menu.getMenu().add(0, 2, 1, R.string.action_new_file);
        menu.getMenu().add(0, 3, 2, R.string.action_refresh);
        menu.setOnMenuItemClickListener(item -> {
            FileBrowserViewModel vm = viewModelFor(activePane);
            switch (item.getItemId()) {
                case 1:
                    showNameDialog(R.string.action_new_folder, R.string.folder_name, vm::createFolder);
                    return true;
                case 2:
                    showNameDialog(R.string.action_new_file, R.string.file_name, vm::createFile);
                    return true;
                case 3:
                    vm.refresh();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void showSelectionMoreMenu() {
        Set<FilePath> selection = viewModelFor(activePane).selection().getValue();
        if (selection == null || selection.isEmpty()) {
            return;
        }
        boolean single = selection.size() == 1;
        FilePath singlePath = single ? selection.iterator().next() : null;
        FileNode singleNode = single ? findVisibleNode(singlePath) : null;

        NodeActionsBottomSheet sheet = NodeActionsBottomSheet
                .newInstance(single ? singlePath.name() : selection.size() + " items");
        sheet.setListener(action -> handleSelectionAction(action, singleNode));
        sheet.show(getChildFragmentManager(), "selection-more");
    }

    private void handleSelectionAction(NodeActionsBottomSheet.Action action, @Nullable FileNode singleNode) {
        FileBrowserViewModel vm = viewModelFor(activePane);
        switch (action) {
            case DELETE:
                confirmDeleteSelected();
                break;
            case SHARE:
                shareSelected();
                break;
            case RENAME:
                if (singleNode == null) {
                    toast(getString(R.string.selection_single_only));
                } else {
                    showNameDialog(R.string.action_rename, R.string.file_name,
                            name -> vm.rename(singleNode, name));
                }
                break;
            case PROPERTIES:
                if (singleNode == null) {
                    toast(getString(R.string.selection_single_only));
                } else {
                    showProperties(singleNode);
                }
                break;
            case OPEN_WITH:
                if (singleNode == null) {
                    toast(getString(R.string.selection_single_only));
                } else {
                    openWith(singleNode);
                }
                break;
            case COPY:
            case MOVE:
            case TOOLS:
            case COMPRESS:
            case BOOKMARK:
            default:
                toast(getString(action.labelRes) + " — coming in Phase 2B");
                break;
        }
    }

    private void confirmDeleteSelected() {
        FileBrowserViewModel vm = viewModelFor(activePane);
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
        FileBrowserViewModel vm = viewModelFor(activePane);
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

    @Nullable
    private FileNode findVisibleNode(FilePath path) {
        FileListAdapter adapter = activePane == PANE_LEFT ? leftAdapter : rightAdapter;
        if (adapter == null) {
            return null;
        }
        for (int i = 0; i < adapter.getItemCount(); i++) {
            FileNode node = adapter.getCurrentList().get(i);
            if (node.path().equals(path)) {
                return node;
            }
        }
        return null;
    }

    private void showNameDialog(int titleRes, int hintRes, NameCallback callback) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint(hintRes);
        new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_create, (dialog, which) -> callback.onName(input.getText().toString()))
                .show();
    }

    private void openFile(int pane, FileNode node) {
        FileOpener.Action action = FileOpener.decide(node);
        switch (action) {
            case OPEN_TEXT:
                if (node.path().isLocal()) {
                    Intent intent = new Intent(requireContext(), TextEditorActivity.class);
                    intent.putExtra(TextEditorActivity.EXTRA_PATH, node.path().path());
                    startActivity(intent);
                } else {
                    toast("Editing inside archive: coming in Phase 2B");
                }
                break;
            case OPEN_ARCHIVE:
                if (node.path().isLocal()) {
                    viewModelFor(pane).openArchive(node.path());
                } else {
                    toast("Nested archive: coming in Phase 2B");
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
                    toast("Opening files inside archive: coming in Phase 2B");
                }
                break;
        }
    }

    private void openWith(FileNode node) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    new File(node.path().path()));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, MimeTypes.detect(node.name()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.action_open_with)));
        } catch (IllegalArgumentException | ActivityNotFoundException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    private void showProperties(FileNode node) {
        PropertiesDialogFragment.newInstance(node.path().toString())
                .show(getParentFragmentManager(), "properties");
    }

    private FileBrowserViewModel viewModelFor(int pane) {
        if (pane == PANE_RIGHT && dualPane) {
            return rightViewModel;
        }
        return leftViewModel;
    }

    private static List<FileNode> withParent(FilePath path, List<FileNode> nodes) {
        List<FileNode> out = new ArrayList<>();
        if (!"/".equals(path.path())) {
            out.add(new ParentFileNode(parentFor(path)));
        }
        out.addAll(nodes);
        return out;
    }

    private static FilePath parentFor(FilePath path) {
        if (path.isArchive() && "/".equals(path.path())) {
            FilePath archiveFile = FilePath.parse(path.authority());
            return archiveFile.parent();
        }
        return path.parent();
    }

    private static String displayPath(FilePath path) {
        if (path.isArchive()) {
            FilePath archiveFile = FilePath.parse(path.authority());
            return archiveFile.path() + "!" + path.path();
        }
        return "/".equals(path.path()) ? "/" : path.path() + "/";
    }

    private void toast(CharSequence message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private interface NameCallback {
        void onName(String name);
    }
}

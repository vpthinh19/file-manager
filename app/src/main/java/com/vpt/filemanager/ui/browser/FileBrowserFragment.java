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
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
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

    public FileBrowserFragment() {
        super(R.layout.fragment_file_browser);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentFileBrowserBinding.bind(view);
        leftViewModel = new ViewModelProvider(this).get("left", FileBrowserViewModel.class);
        rightViewModel = new ViewModelProvider(this).get("right", FileBrowserViewModel.class);
        leftAdapter = new FileListAdapter(PANE_LEFT, this);
        rightAdapter = new FileListAdapter(PANE_RIGHT, this);

        binding.rvLeft.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRight.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLeft.setAdapter(leftAdapter);
        binding.rvRight.setAdapter(rightAdapter);

        applyInsets();
        configureActions();
        observePane(PANE_LEFT, leftViewModel, leftAdapter);
        observePane(PANE_RIGHT, rightViewModel, rightAdapter);
        installBackHandler();

        String home = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (savedInstanceState == null) {
            if (leftViewModel.currentPath() == null) {
                leftViewModel.navigateTo(FilePath.local(home));
            }
            if (rightViewModel.currentPath() == null) {
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
        activePane = pane;
        if (node instanceof ParentFileNode) {
            viewModelFor(pane).navigateTo(node.path());
            return;
        }
        if (node.isDirectory()) {
            viewModelFor(pane).onItemClicked(node);
        } else {
            openFile(node);
        }
    }

    @Override
    public void onFileLongClicked(int pane, FileNode node) {
        activePane = pane;
        if (node instanceof ParentFileNode) {
            return;
        }
        showNodeActions(node);
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (root, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            binding.header.setPadding(
                    binding.header.getPaddingLeft(),
                    top,
                    binding.header.getPaddingRight(),
                    binding.header.getPaddingBottom());
            binding.bottomBar.setPadding(
                    binding.bottomBar.getPaddingLeft(),
                    binding.bottomBar.getPaddingTop(),
                    binding.bottomBar.getPaddingRight(),
                    bottom);
            return insets;
        });
    }

    private void configureActions() {
        binding.btnUp.setOnClickListener(v -> viewModelFor(activePane).navigateUp());
        binding.btnBack.setOnClickListener(v -> viewModelFor(activePane).navigateUp());
        binding.btnForward.setOnClickListener(v -> toast("Forward history is not implemented yet"));
        binding.btnSwap.setOnClickListener(v -> {
            activePane = activePane == PANE_LEFT ? PANE_RIGHT : PANE_LEFT;
            updateActiveHeader();
        });
        binding.btnTrash.setOnClickListener(v -> viewModelFor(activePane).openTrash());
        binding.btnAdd.setOnClickListener(v -> showCreateMenu(binding.btnAdd));
        binding.btnMore.setOnClickListener(v -> showBrowserMenu(binding.btnMore));
        binding.btnMenu.setOnClickListener(v -> showBrowserMenu(binding.btnMenu));
    }

    private void observePane(int pane, FileBrowserViewModel viewModel, FileListAdapter adapter) {
        viewModel.uiState().observe(getViewLifecycleOwner(), state -> {
            if (pane == activePane) {
                binding.progress.setVisibility(state instanceof FileBrowserViewModel.UiState.Loading ? View.VISIBLE : View.GONE);
                binding.tvEmpty.setVisibility(state instanceof FileBrowserViewModel.UiState.Empty ? View.VISIBLE : View.GONE);
            }
            if (state instanceof FileBrowserViewModel.UiState.Content) {
                FileBrowserViewModel.UiState.Content content = (FileBrowserViewModel.UiState.Content) state;
                adapter.submitList(withParent(content.path, content.nodes));
                if (pane == activePane) {
                    showHeader(content);
                }
            } else if (state instanceof FileBrowserViewModel.UiState.Empty) {
                FileBrowserViewModel.UiState.Empty empty = (FileBrowserViewModel.UiState.Empty) state;
                adapter.submitList(withParent(empty.path, List.of()));
                if (pane == activePane) {
                    binding.tvPath.setText(displayPath(empty.path));
                    binding.tvStats.setText("Folders: 0  Files: 0");
                }
            } else if (state instanceof FileBrowserViewModel.UiState.Error) {
                FileBrowserViewModel.UiState.Error error = (FileBrowserViewModel.UiState.Error) state;
                adapter.submitList(withParent(error.path, List.of()));
                if (pane == activePane) {
                    binding.tvPath.setText(displayPath(error.path));
                    binding.tvStats.setText(R.string.unavailable);
                    toast(error.message == null ? getString(R.string.unavailable) : error.message);
                }
            }
        });
        viewModel.events().observe(getViewLifecycleOwner(), this::toast);
    }

    private void installBackHandler() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!viewModelFor(activePane).navigateUp()) {
                    setEnabled(false);
                    requireActivity().onBackPressed();
                }
            }
        });
    }

    private void showHeader(FileBrowserViewModel.UiState.Content content) {
        binding.tvPath.setText(displayPath(content.path));
        binding.tvStats.setText("Folders: " + content.folderCount
                + "  Files: " + content.fileCount
                + "  Disk: " + ByteSize.format(content.freeBytes)
                + "/" + ByteSize.format(content.totalBytes));
    }

    private void updateActiveHeader() {
        FilePath path = viewModelFor(activePane).currentPath();
        if (path != null) {
            binding.tvPath.setText(displayPath(path));
        }
    }

    private void showCreateMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(R.string.action_new_file);
        menu.getMenu().add(R.string.action_new_folder);
        menu.setOnMenuItemClickListener(item -> {
            if (getString(R.string.action_new_file).contentEquals(item.getTitle())) {
                showNameDialog(R.string.action_new_file, R.string.file_name,
                        name -> viewModelFor(activePane).createFile(name));
            } else {
                showNameDialog(R.string.action_new_folder, R.string.folder_name,
                        name -> viewModelFor(activePane).createFolder(name));
            }
            return true;
        });
        menu.show();
    }

    private void showBrowserMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(R.string.action_refresh);
        menu.getMenu().add(R.string.action_trash);
        menu.getMenu().add("Go to /");
        menu.getMenu().add("Go to /system");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (getString(R.string.action_refresh).equals(title)) {
                viewModelFor(activePane).refresh();
            } else if (getString(R.string.action_trash).equals(title)) {
                viewModelFor(activePane).openTrash();
            } else if ("Go to /system".equals(title)) {
                viewModelFor(activePane).navigateTo(FilePath.local("/system"));
            } else {
                viewModelFor(activePane).navigateTo(FilePath.local("/"));
            }
            return true;
        });
        menu.show();
    }

    private void showNodeActions(FileNode node) {
        PopupMenu menu = new PopupMenu(requireContext(), binding.root);
        menu.getMenu().add(R.string.action_copy);
        menu.getMenu().add(R.string.action_move);
        menu.getMenu().add(R.string.action_delete);
        menu.getMenu().add(R.string.action_rename);
        menu.getMenu().add(R.string.action_tools);
        menu.getMenu().add(R.string.action_compress);
        menu.getMenu().add(R.string.properties);
        menu.getMenu().add(R.string.action_share);
        menu.getMenu().add(R.string.action_open_with);
        menu.getMenu().add(R.string.action_bookmark);
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (getString(R.string.properties).equals(title)) {
                showProperties(node);
            } else if (getString(R.string.action_delete).equals(title)) {
                confirmDelete(node);
            } else if (getString(R.string.action_rename).equals(title)) {
                showNameDialog(R.string.action_rename, R.string.file_name,
                        name -> viewModelFor(activePane).rename(node, name));
            } else if (getString(R.string.action_open_with).equals(title)) {
                openWith(node);
            } else if (getString(R.string.action_share).equals(title)) {
                share(node);
            } else {
                toast(title + " is not implemented yet");
            }
            return true;
        });
        menu.show();
    }

    private void confirmDelete(FileNode node) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_delete)
                .setMessage(node.name())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> viewModelFor(activePane).delete(node))
                .show();
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

    private void openFile(FileNode node) {
        if (isTextLike(node.name())) {
            Intent intent = new Intent(requireContext(), TextEditorActivity.class);
            intent.putExtra(TextEditorActivity.EXTRA_PATH, node.path().path());
            startActivity(intent);
        } else {
            openWith(node);
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

    private void share(FileNode node) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    new File(node.path().path()));
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(MimeTypes.detect(node.name()));
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
        } catch (IllegalArgumentException | ActivityNotFoundException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    private void showProperties(FileNode node) {
        PropertiesDialogFragment.newInstance(node.path().toString())
                .show(getParentFragmentManager(), "properties");
    }

    private FileBrowserViewModel viewModelFor(int pane) {
        return pane == PANE_LEFT ? leftViewModel : rightViewModel;
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
        if ("/storage/emulated/0".equals(path.path()) || "/sdcard".equals(path.path())) {
            return FilePath.local("/");
        }
        return path.parent();
    }

    private static String displayPath(FilePath path) {
        return "/".equals(path.path()) ? "/" : path.path() + "/";
    }

    private static boolean isTextLike(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".json")
                || lower.endsWith(".xml")
                || lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".js")
                || lower.endsWith(".java")
                || lower.endsWith(".kt")
                || lower.endsWith(".log")
                || lower.endsWith(".prop")
                || !lower.contains(".");
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private interface NameCallback {
        void onName(String name);
    }
}

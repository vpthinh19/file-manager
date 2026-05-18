package com.vpt.filemanager.ui.browser;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.core.storage.StorageScope;
import com.vpt.filemanager.databinding.FragmentPaneBinding;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.ui.dualpane.PaneController;

@AndroidEntryPoint
public final class PaneFragment extends Fragment implements FileListAdapter.Listener {
    public static final String ARG_PANE_ID = "pane_id";

    private FragmentPaneBinding binding;
    private FileListAdapter adapter;
    private PaneViewModel viewModel;
    private PaneController controller;

    public static PaneFragment newInstance(@NonNull String paneId) {
        PaneFragment fragment = new PaneFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PANE_ID, paneId);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    public String paneId() {
        return requireArguments().getString(ARG_PANE_ID, "");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Fragment parent = getParentFragment();
        if (parent instanceof PaneController c) {
            controller = c;
        } else {
            throw new IllegalStateException("PaneFragment must be hosted by a PaneController parent");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        controller = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pane, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentPaneBinding.bind(view);
        viewModel = controller.viewModelForPane(paneId());

        adapter = new FileListAdapter(this);
        binding.rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rv.setAdapter(adapter);
        binding.rv.setItemAnimator(null);
        binding.rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                        && controller != null) {
                    controller.onPaneActivated(paneId());
                }
                return false;
            }
        });

        observeViewModel();

        if (savedInstanceState == null && viewModel.currentPath() == null) {
            viewModel.navigateTo(StorageScope.rootPath());
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onFileClicked(@NonNull FileNode node) {
        if (controller != null) {
            controller.onPaneActivated(paneId());
        }
        if (viewModel.isInSelectionMode()) {
            viewModel.toggleSelect(node);
            return;
        }
        if (node instanceof ParentFileNode) {
            viewModel.navigateTo(node.path());
            return;
        }
        if (node.isDirectory()) {
            viewModel.navigateTo(node.path());
        } else if (controller != null) {
            controller.onOpenFile(paneId(), node);
        }
    }

    @Override
    public void onFileLongClicked(@NonNull FileNode node) {
        if (controller != null) {
            controller.onPaneActivated(paneId());
        }
        if (node instanceof ParentFileNode) {
            return;
        }
        viewModel.toggleSelect(node);
    }

    /**
     * Toggles the active-pane indicator on the container's foreground. The container is the
     * FrameLayout root of {@code fragment_pane.xml}; its foreground drawable is a state-list that
     * paints a 2dp primary-colored border when {@code activated == true}.
     */
    public void setPaneActivated(boolean active) {
        if (binding != null) {
            binding.paneRoot.setActivated(active);
        }
    }

    private void observeViewModel() {
        viewModel.uiState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null) {
                return;
            }
            binding.progress.setVisibility(state instanceof PaneViewModel.UiState.Loading ? View.VISIBLE : View.GONE);
            binding.tvEmpty.setVisibility(state instanceof PaneViewModel.UiState.Empty ? View.VISIBLE : View.GONE);
            if (state instanceof PaneViewModel.UiState.Content content) {
                adapter.submitList(withParent(content.path, content.nodes));
            } else if (state instanceof PaneViewModel.UiState.Roots roots) {
                adapter.submitList(new ArrayList<>(roots.roots));
            } else if (state instanceof PaneViewModel.UiState.Empty empty) {
                adapter.submitList(withParent(empty.path, List.of()));
            } else if (state instanceof PaneViewModel.UiState.Error error) {
                adapter.submitList(withParent(error.path, List.of()));
            }
        });
        viewModel.selection().observe(getViewLifecycleOwner(), adapter::setSelection);
    }

    private static List<FileNode> withParent(FilePath path, List<FileNode> nodes) {
        List<FileNode> out = new ArrayList<>(nodes.size() + 1);
        if (StorageScope.canGoUp(path)) {
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
}

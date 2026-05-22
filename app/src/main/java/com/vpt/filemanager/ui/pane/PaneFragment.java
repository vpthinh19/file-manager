package com.vpt.filemanager.ui.pane;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.rules.storage.StorageScope;
import com.vpt.filemanager.databinding.FragmentPaneBinding;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.navigation.NavigateToParentOperation;
import com.vpt.filemanager.ui.pane.list.FileListAdapter;

/**
 * 1 pane trong dual-pane host. Hiển thị {@link RecyclerView} các {@link VirtualNode} children
 * của currentPath. Phase R-5b: migrated FileNode → VirtualNode; click flow giữ parity nhưng dispatch
 * qua {@link VirtualNode#isParent()} / {@code isFolder()} thay vì {@code instanceof ParentFileNode}.
 *
 * <p>Parent ".." row được wrapped trong {@link #withParent(NodePath, List)} — PaneViewModel emit
 * children pure (không có parent), Fragment add marker tại UI layer.
 */
@AndroidEntryPoint
public final class PaneFragment extends Fragment implements FileListAdapter.Listener {
    public static final String ARG_PANE_ID = "pane_id";
    private static final NavigateToParentOperation NAVIGATE_TO_PARENT =
            new NavigateToParentOperation();

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
        // Fixed-size rows let RV skip a measure pass. Row height = 46dp (row_file_node.xml).
        binding.rv.setHasFixedSize(true);
        // Tight 120ms cross-fade per item — DefaultItemAnimator fade-out rows DiffUtil removed
        // (folder leaving) song song fade-in rows DiffUtil inserted. KHÔNG layoutAnimation,
        // KHÔNG manual RV alpha trick: cross-fade sync với diff commit.
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setAddDuration(120);
        animator.setRemoveDuration(120);
        animator.setChangeDuration(120);
        animator.setMoveDuration(120);
        binding.rv.setItemAnimator(animator);
        binding.rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,
                                                 @NonNull android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                        && controller != null) {
                    controller.onPaneActivated(paneId());
                }
                return false;
            }
        });

        observeViewModel();

        // Sau process death Fragment receive savedInstanceState non-null nhưng VM freshly created
        // chưa có currentPath — phải navigate, else uiState stuck Loading. PaneViewModel restore
        // path từ SavedStateHandle bên trong; guard này catch cold-start.
        if (viewModel.currentPath() == null) {
            viewModel.navigateTo(StorageScope.rootPath());
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onFileClicked(@NonNull VirtualNode node) {
        if (controller != null) {
            controller.onPaneActivated(paneId());
        }
        if (viewModel.isInSelectionMode()) {
            viewModel.toggleSelect(node);
            return;
        }
        if (node.isParent()) {
            // Parent marker path = parent's path. navigateTo handles it.
            viewModel.navigateTo(node.path());
            return;
        }
        // Phase R-7b: trash entry không openable (không có file backend) — tap = enter selection
        // semantic. Bookmark children scheme=file → click chuẩn (passthrough). Trash pane phải
        // detect qua currentPath, KHÔNG qua node.path().scheme() vì node có thể là parent marker.
        NodePath current = viewModel.currentPath();
        if (current != null && current.isTrash()) {
            viewModel.enterSelectionAndToggle(node);
            return;
        }
        if (node.isFolder()) {
            viewModel.navigateTo(node.path());
        } else if (controller != null) {
            controller.onOpenFile(paneId(), node);
        }
    }

    @Override
    public void onFileLongClicked(@NonNull VirtualNode node) {
        if (controller != null) {
            controller.onPaneActivated(paneId());
        }
        if (node.isParent()) {
            return;
        }
        // Long-press = entry point vào selection mode. Phase R-7a: dùng dedicated method để
        // VM bật flag selectionMode trước khi add. Subsequent taps đi qua toggleSelect (đã gated).
        viewModel.enterSelectionAndToggle(node);
    }

    /**
     * Toggle active-pane indicator trên container foreground. Container là FrameLayout root
     * của fragment_pane.xml; foreground drawable = state-list paint 2dp border khi activated.
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
            boolean isLoading = state instanceof PaneViewModel.UiState.Loading;
            binding.progress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) {
                // Keep old folder rows visible underneath spinner — less destructive than flicker.
                return;
            }
            adapter.submitList(listFor(state));
        });
        viewModel.selection().observe(getViewLifecycleOwner(), adapter::setSelection);
    }

    @NonNull
    private static List<VirtualNode> listFor(@NonNull PaneViewModel.UiState state) {
        if (state instanceof PaneViewModel.UiState.Content c) {
            return withParent(c.path, c.nodes);
        }
        if (state instanceof PaneViewModel.UiState.Empty e) {
            return withParent(e.path, List.of());
        }
        if (state instanceof PaneViewModel.UiState.Error e) {
            return withParent(e.path, List.of());
        }
        return Collections.emptyList();
    }

    private static List<VirtualNode> withParent(NodePath path, List<VirtualNode> nodes) {
        List<VirtualNode> out = new ArrayList<>(nodes.size() + 1);
        if (StorageScope.canGoUp(path)) {
            out.add(VirtualNode.parent(parentFor(path)));
        }
        out.addAll(nodes);
        return out;
    }

    private static NodePath parentFor(NodePath path) {
        return NAVIGATE_TO_PARENT.execute(new NavigateToParentOperation.Input(path)).parentPath;
    }
}

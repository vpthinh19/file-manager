package com.vpt.filemanager.ui.browser;

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
    private DefaultItemAnimator listAnimator;
    private PaneViewModel viewModel;
    private PaneController controller;
    /**
     * The path the adapter is currently rendering. Drives the choice between MT-drop "navigation"
     * animation (path-change) and a quiet incremental update (selection toggle, post-CRUD refresh).
     * Null until the first non-Loading state arrives — that lets the first paint cascade in too.
     */
    @Nullable
    private FilePath currentDisplayedPath;

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
        // Fixed-size rows let RecyclerView skip a measure pass. Row height is 46dp (see
        // row_file_node.xml) — single source of truth lives there, not in comments here.
        binding.rv.setHasFixedSize(true);
        // Tight 120ms add/remove/change/move — used ONLY for incremental updates (selection toggle,
        // post-CRUD refresh of the same folder). On path-change navigation the animator is swapped
        // out for null so the old folder's rows disappear instantly (no slow fade-out preceding the
        // MT-drop cascade), then restored once the new list commits.
        listAnimator = new DefaultItemAnimator();
        listAnimator.setAddDuration(120);
        listAnimator.setRemoveDuration(120);
        listAnimator.setChangeDuration(120);
        listAnimator.setMoveDuration(120);
        binding.rv.setItemAnimator(listAnimator);
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

        // After process death the Fragment receives a non-null savedInstanceState yet the freshly
        // re-created ViewModel has no currentPath — we must still navigate, otherwise uiState stays
        // at the initial Loading and the user sees a spinner forever. The SavedStateHandle restore
        // path inside PaneViewModel handles MT-style "remember where I left off"; this guard only
        // catches the cold-start case.
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
     * paints a 2dp border when {@code activated == true} (white in dark mode, black in light).
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
                // Keep the old folder's rows visible underneath the spinner while the new listing
                // is in flight — feels less destructive than clearing to empty + flicker.
                return;
            }

            FilePath newPath = pathOf(state);
            List<FileNode> newList = listFor(state);
            boolean pathChanged = newPath != null
                    && (currentDisplayedPath == null || !currentDisplayedPath.equals(newPath));
            currentDisplayedPath = newPath;

            if (pathChanged) {
                // Navigation: hard-swap the list (no per-row fade-out) so the user never sees the
                // previous folder's rows linger or animate out — then cascade the MT-drop on the
                // new rows via scheduleLayoutAnimation.
                binding.rv.setItemAnimator(null);
                adapter.submitList(newList, this::onNavigationCommitted);
            } else {
                // Incremental update for the SAME folder (selection toggle, post-CRUD refresh).
                // Keep the animator so add/remove flows are smooth and DO NOT re-trigger the
                // layoutAnimation — re-running it on every selection change is the jitter the user
                // reported, not the desired effect.
                if (binding.rv.getItemAnimator() == null) {
                    binding.rv.setItemAnimator(listAnimator);
                }
                adapter.submitList(newList);
            }
        });
        viewModel.selection().observe(getViewLifecycleOwner(), adapter::setSelection);
    }

    /**
     * Commit callback for a navigation submitList: restore the item animator (disabled during the
     * hard swap) and kick the MT-drop cascade declared in {@code fragment_pane.xml}.
     */
    private void onNavigationCommitted() {
        if (binding == null) {
            return;
        }
        binding.rv.setItemAnimator(listAnimator);
        binding.rv.scheduleLayoutAnimation();
    }

    @Nullable
    private static FilePath pathOf(@NonNull PaneViewModel.UiState state) {
        if (state instanceof PaneViewModel.UiState.Content c) return c.path;
        if (state instanceof PaneViewModel.UiState.Empty e) return e.path;
        if (state instanceof PaneViewModel.UiState.Error e) return e.path;
        if (state instanceof PaneViewModel.UiState.Roots r) return r.path;
        return null;
    }

    @NonNull
    private static List<FileNode> listFor(@NonNull PaneViewModel.UiState state) {
        if (state instanceof PaneViewModel.UiState.Content c) {
            return withParent(c.path, c.nodes);
        }
        if (state instanceof PaneViewModel.UiState.Roots r) {
            return new ArrayList<>(r.roots);
        }
        if (state instanceof PaneViewModel.UiState.Empty e) {
            return withParent(e.path, List.of());
        }
        if (state instanceof PaneViewModel.UiState.Error e) {
            return withParent(e.path, List.of());
        }
        return Collections.emptyList();
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

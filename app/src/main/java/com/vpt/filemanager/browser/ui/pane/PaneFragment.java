package com.vpt.filemanager.browser.ui.pane;

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

import dagger.hilt.android.AndroidEntryPoint;

import com.vpt.filemanager.R;
import com.vpt.filemanager.browser.action.browse.ActivateItemAction;
import com.vpt.filemanager.browser.action.browse.ActivatePaneAction;
import com.vpt.filemanager.browser.action.selection.ToggleSelectionAction;
import com.vpt.filemanager.databinding.FragmentPaneBinding;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.ui.pane.list.FileListAdapter;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.browser.workspace.state.PaneState;

@AndroidEntryPoint
public final class PaneFragment extends Fragment implements FileListAdapter.Listener {
    private static final String ARG_PANE_ID = "pane_id";
    private FragmentPaneBinding binding;
    private FileListAdapter adapter;
    private PaneController controller;

    public static PaneFragment newInstance(@NonNull String paneId) {
        PaneFragment fragment = new PaneFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PANE_ID, paneId);
        fragment.setArguments(args);
        return fragment;
    }

    private PaneId paneId() {
        return "right".equals(requireArguments().getString(ARG_PANE_ID)) ? PaneId.RIGHT : PaneId.LEFT;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Fragment parent = getParentFragment();
        if (!(parent instanceof PaneController value)) {
            throw new IllegalStateException("PaneFragment requires PaneController");
        }
        controller = value;
    }

    @Override
    public void onDetach() {
        controller = null;
        super.onDetach();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_pane, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        binding = FragmentPaneBinding.bind(view);
        adapter = new FileListAdapter(this);
        binding.rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rv.setAdapter(adapter);
        binding.rv.setHasFixedSize(true);
        binding.rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView list,
                                                 @NonNull android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    controller.dispatch(new ActivatePaneAction(paneId()));
                }
                return false;
            }
        });
        controller.workspaceState().observe(getViewLifecycleOwner(), workspace -> {
            PaneState pane = workspace.pane(paneId());
            binding.progress.setVisibility(pane.loading ? View.VISIBLE : View.GONE);
            adapter.submitList(pane.items);
            adapter.setSelection(pane.selection);
        });
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onFileClicked(@NonNull Item item) {
        PaneState state = controller.workspaceState().getValue().pane(paneId());
        controller.dispatch(new ActivatePaneAction(paneId()));
        if (state.selectionMode || (state.path != null && state.path.isTrash())) {
            controller.dispatch(new ToggleSelectionAction(paneId(), item, true));
        } else {
            controller.dispatch(new ActivateItemAction(paneId(), item));
        }
    }

    @Override
    public void onFileLongClicked(@NonNull Item item) {
        controller.dispatch(new ActivatePaneAction(paneId()));
        controller.dispatch(new ToggleSelectionAction(paneId(), item, true));
    }

    public void setPaneActivated(boolean active) {
        if (binding != null) binding.paneRoot.setActivated(active);
    }
}

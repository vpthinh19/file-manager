package com.vpt.filemanager.ui.pane;

import android.os.Bundle;
import android.os.FileObserver;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.core.threading.AppExecutors;
import com.vpt.filemanager.databinding.FragmentPaneBinding;
import com.vpt.filemanager.model.Entry;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.resolver.EntryResolver;
import com.vpt.filemanager.resolver.ResolveResult;
import com.vpt.filemanager.state.ContentState;
import com.vpt.filemanager.state.PaneId;
import com.vpt.filemanager.state.PaneState;
import com.vpt.filemanager.state.StateViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** One independent pane: it resolves and renders only its own observed location. */
@AndroidEntryPoint
public final class PaneFragment extends Fragment implements EntryAdapter.Listener {
    private static final String ARG_PANE = "pane";
    private static final int WATCH_EVENTS = FileObserver.CREATE | FileObserver.DELETE
            | FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE
            | FileObserver.DELETE_SELF | FileObserver.MOVE_SELF;

    @Inject EntryResolver resolver;
    @Inject AppExecutors executors;
    private StateViewModel state;
    private FragmentPaneBinding binding;
    private EntryAdapter adapter;
    @Nullable private FileObserver observer;
    @Nullable private Location requested;

    public static PaneFragment newInstance(PaneId pane) {
        PaneFragment fragment = new PaneFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_PANE, pane.name());
        fragment.setArguments(arguments);
        return fragment;
    }

    private PaneId pane() {
        return PaneId.valueOf(requireArguments().getString(ARG_PANE, PaneId.LEFT.name()));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pane, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding = FragmentPaneBinding.bind(view);
        state = new ViewModelProvider(requireActivity()).get(StateViewModel.class);
        adapter = new EntryAdapter(this);
        binding.rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rv.setAdapter(adapter);
        binding.rv.setHasFixedSize(true);
        binding.rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView list, @NonNull MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) state.activate(pane());
                return false;
            }
        });
        state.pane(pane()).observe(getViewLifecycleOwner(), value -> {
            binding.paneRoot.setActivated(state.activePaneValue() == pane());
            binding.progress.setVisibility(value.loading ? View.VISIBLE : View.GONE);
            adapter.submitList(value.entries);
            adapter.setSelection(value.selection);
            if (!value.location.equals(requested)) load(value.location, value.sort);
        });
        state.activePane().observe(getViewLifecycleOwner(), ignored ->
                binding.paneRoot.setActivated(state.activePaneValue() == pane()));
        state.invalidation().observe(getViewLifecycleOwner(), ignored -> {
            PaneState current = state.current(pane());
            load(current.location, current.sort);
        });
    }

    private void load(Location location, com.vpt.filemanager.model.SortOption sort) {
        requested = location;
        installObserver(location);
        long request = state.beginLoading(pane(), location);
        if (request < 0) return;
        executors.io().execute(() -> {
            try {
                ResolveResult result = resolver.resolve(location);
                executors.main().execute(() -> applyResult(location, sort, request, result));
            } catch (FileOperationException | RuntimeException error) {
                executors.main().execute(() -> state.showFailure(pane(), request, error.getMessage()));
            }
        });
    }

    private void applyResult(Location source, com.vpt.filemanager.model.SortOption sort, long request,
                             ResolveResult result) {
        if (result instanceof ResolveResult.ReplaceLocation replace) {
            state.replaceResolvedLocation(pane(), replace.target());
        } else if (result instanceof ResolveResult.Directory directory) {
            List<Entry> entries = new ArrayList<>(directory.entries());
            entries.sort(sort.comparator());
            state.showEntries(pane(), request, entries);
        } else if (result instanceof ResolveResult.Content content) {
            state.showEntries(pane(), request, List.of());
            state.showContent(new ContentState(pane(), content.source(), content.localPath(),
                    content.displayName(), content.kind(), content.readOnly(), content.archiveEntry()));
        }
    }

    @Override
    public void onFileClicked(@NonNull Entry entry) {
        PaneState paneState = state.current(pane());
        state.activate(pane());
        if (paneState.selectionMode || paneState.location.isTrash()) {
            state.toggleSelection(pane(), entry);
        } else {
            state.navigate(pane(), entry.location());
        }
    }

    @Override
    public void onFileLongClicked(@NonNull Entry entry) {
        state.activate(pane());
        state.toggleSelection(pane(), entry);
    }

    private void installObserver(Location location) {
        if (observer != null) observer.stopWatching();
        File watched = null;
        if (location.isSearch()) watched = new File(location.physicalPath());
        else if (location.isArchiveEntry()) watched = new File(location.physicalPath()).getParentFile();
        else if (location.isStorage()) {
            File current = new File(location.physicalPath());
            watched = current.isDirectory() ? current : current.getParentFile();
        }
        if (watched == null || !watched.isDirectory()) {
            observer = null;
            return;
        }
        observer = new FileObserver(watched, WATCH_EVENTS) {
            @Override public void onEvent(int event, @Nullable String name) {
                executors.main().execute(() -> state.invalidate(location));
            }
        };
        observer.startWatching();
    }

    @Override
    public void onDestroyView() {
        if (observer != null) observer.stopWatching();
        observer = null;
        binding = null;
        super.onDestroyView();
    }
}

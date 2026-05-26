package com.vpt.filemanager.component.pane;

import android.os.Bundle;
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
import com.vpt.filemanager.component.content.OpenedContent;
import com.vpt.filemanager.component.dialog.OpenAsDialogComponent;
import com.vpt.filemanager.component.state.StateViewModel;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.entry.SortOption;
import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.core.threading.AppExecutors;
import com.vpt.filemanager.databinding.FragmentPaneBinding;
import com.vpt.filemanager.handler.HandlerResult;
import com.vpt.filemanager.storage.InvalidationSubscription;
import com.vpt.filemanager.storage.facade.OpenMode;
import com.vpt.filemanager.storage.facade.OpenResult;
import com.vpt.filemanager.storage.facade.StorageFacade;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** One independent pane; all resource operations cross the facade boundary. */
@AndroidEntryPoint
public final class PaneFragment extends Fragment implements EntryAdapter.Listener {
    private static final String ARG_PANE = "pane";

    @Inject StorageFacade facade;
    @Inject AppExecutors executors;
    private StateViewModel state;
    private FragmentPaneBinding binding;
    private EntryAdapter adapter;
    @Nullable private InvalidationSubscription subscription;
    @Nullable private Path requested;
    @Nullable private Path fileBeingOpened;
    private boolean inFlight;
    private boolean pendingReload;
    @Nullable private Path pendingLocation;
    @Nullable private SortOption pendingSort;

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
            if (!value.location.equals(requested)) requestLoad(value.location, value.sort, OpenMode.DEFAULT);
        });
        state.activePane().observe(getViewLifecycleOwner(), ignored ->
                binding.paneRoot.setActivated(state.activePaneValue() == pane()));
        state.visibleRefresh().observe(getViewLifecycleOwner(), ignored -> {
            PaneState current = state.current(pane());
            requestLoad(current.location, current.sort, OpenMode.DEFAULT);
        });
    }

    private void requestLoad(Path location, SortOption sort, OpenMode mode) {
        if (inFlight) {
            pendingReload = true;
            pendingLocation = location;
            pendingSort = sort;
            return;
        }
        load(location, sort, mode);
    }

    private void load(Path location, SortOption sort, OpenMode mode) {
        inFlight = true;
        requested = location;
        installObserver(location);
        long request = state.beginLoading(pane(), location);
        if (request < 0) {
            completeLoad();
            return;
        }
        executors.io().execute(() -> {
            try {
                OpenResult result = sorted(facade.open(location, mode), sort);
                executors.main().execute(() -> applyResult(location, request, result));
            } catch (Exception error) {
                executors.main().execute(() -> applyFailure(location, request, error));
            } catch (LinkageError error) {
                executors.main().execute(() -> applyFailure(location, request, error));
            }
        });
    }

    private OpenResult sorted(OpenResult result, SortOption sort) {
        if (!(result instanceof OpenResult.Directory directory)) return result;
        List<Entry> entries = new ArrayList<>(directory.entries());
        entries.sort(sort.comparator());
        return new OpenResult.Directory(directory.canonicalPath(), entries, directory.capabilities());
    }

    private void applyResult(Path source, long request, OpenResult result) {
        if (result instanceof OpenResult.Directory directory) {
            requested = directory.canonicalPath();
            installObserver(directory.canonicalPath());
            fileBeingOpened = null;
            state.showDirectory(pane(), request, directory.canonicalPath(), directory.entries(),
                    directory.capabilities());
            completeLoad();
            return;
        }
        if (result instanceof OpenResult.NeedsOpenAs choose) {
            completeLoad();
            OpenAsDialogComponent.show(requireContext(), fileName(choose.source()),
                    mode -> requestLoad(source, state.current(pane()).sort, mode),
                    () -> state.returnFromOpenedFile(pane(), request, source, null));
            return;
        }
        HandlerResult handled = ((OpenResult.Content) result).handled();
        fileBeingOpened = null;
        if (handled instanceof HandlerResult.OpenContent content) {
            state.showEntries(pane(), request, List.of());
            Path archiveEntry = content.source().isInsideArchive() ? content.source() : null;
            state.showContent(new OpenedContent(pane(), content.source(), content.localPath(),
                    facade.contentUri(content.localPath()).toString(), fileName(content.source()),
                    content.type(), content.readOnly(), archiveEntry));
        } else if (handled instanceof HandlerResult.LaunchIntent launch) {
            state.showEntries(pane(), request, List.of());
            state.showContent(new OpenedContent(pane(), launch.source(), launch.localPath(),
                    facade.contentUri(launch.localPath()).toString(), fileName(launch.source()),
                    ContentType.OTHER, true, null));
        }
        completeLoad();
    }

    private void applyFailure(Path location, long request, Throwable error) {
        String message = error.getMessage();
        if (location.equals(fileBeingOpened)) {
            fileBeingOpened = null;
            state.returnFromOpenedFile(pane(), request, location, message);
        } else {
            state.showFailure(pane(), request, message);
        }
        completeLoad();
    }

    private void completeLoad() {
        inFlight = false;
        if (!pendingReload) return;
        pendingReload = false;
        Path location = pendingLocation;
        SortOption sort = pendingSort;
        pendingLocation = null;
        pendingSort = null;
        if (location != null && sort != null) requestLoad(location, sort, OpenMode.DEFAULT);
    }

    @Override
    public void onFileClicked(@NonNull Entry entry) {
        PaneState paneState = state.current(pane());
        state.activate(pane());
        if (paneState.selectionMode || paneState.location.isTrash()) {
            state.toggleSelection(pane(), entry);
        } else {
            if (!entry.isFolder()) fileBeingOpened = entry.path();
            state.navigate(pane(), entry.path());
        }
    }

    @Override
    public void onFileLongClicked(@NonNull Entry entry) {
        state.activate(pane());
        state.toggleSelection(pane(), entry);
    }

    private void installObserver(Path location) {
        if (subscription != null) subscription.close();
        subscription = null;
        try {
            subscription = facade.observe(location, () -> {
                PaneState current = state.current(pane());
                requestLoad(current.location, current.sort, OpenMode.DEFAULT);
            });
        } catch (Exception ignored) {
            // Listing reports actionable errors; observation is optional.
        }
    }

    private static String fileName(Path path) {
        String serialized = path.isInsideArchive() ? path.archiveInnerPath() : path.storagePath();
        int slash = serialized.lastIndexOf('/');
        return slash < 0 ? serialized : serialized.substring(slash + 1);
    }

    @Override
    public void onDestroyView() {
        if (subscription != null) subscription.close();
        subscription = null;
        binding = null;
        super.onDestroyView();
    }
}

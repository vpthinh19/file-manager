package com.vpt.filemanager.ui.trash;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import dagger.hilt.android.AndroidEntryPoint;

import com.vpt.filemanager.R;
import com.vpt.filemanager.ui.DrawerHost;

/**
 * Trash screen — list of recoverable items + restore/empty actions. Hosted inside the same
 * {@code R.id.fragment_container} as the dual-pane browser; {@code MainActivity.showTrash} swaps
 * us in via {@code addToBackStack} so back press pops us off cleanly.
 */
@AndroidEntryPoint
public final class TrashFragment extends Fragment {
    private TrashViewModel vm;
    private TrashAdapter adapter;
    private RecyclerView recycler;
    private TextView empty;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(this).get(TrashViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        recycler = view.findViewById(R.id.recycler);
        empty = view.findViewById(R.id.empty_placeholder);

        toolbar.setNavigationOnClickListener(v -> {
            if (requireActivity() instanceof DrawerHost host) {
                host.openDrawer();
            }
        });
        toolbar.inflateMenu(R.menu.menu_trash);
        toolbar.setOnMenuItemClickListener(this::onMenu);

        applyStatusBarInsets(view);

        adapter = new TrashAdapter(this::confirmRestore);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        vm.entries().observe(getViewLifecycleOwner(), entries -> {
            adapter.submitList(entries);
            boolean hasItems = entries != null && !entries.isEmpty();
            recycler.setVisibility(hasItems ? View.VISIBLE : View.GONE);
            empty.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        });
        vm.events().observe(getViewLifecycleOwner(),
                msg -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show());
    }

    private boolean onMenu(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_empty_trash) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.trash_empty_title)
                    .setMessage(R.string.trash_empty_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.action_empty_trash, (d, w) -> vm.empty())
                    .show();
            return true;
        }
        return false;
    }

    private void confirmRestore(@NonNull com.vpt.filemanager.data.db.entity.TrashEntryEntity entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_restore)
                .setMessage(getString(R.string.trash_restore_confirm, entry.displayName))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_restore, (d, w) -> vm.restore(entry.id))
                .show();
    }

    private void applyStatusBarInsets(@NonNull View root) {
        View appbar = root.findViewById(R.id.appbar);
        View toolbar = root.findViewById(R.id.toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(appbar, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            toolbar.setPadding(toolbar.getPaddingLeft(), top,
                    toolbar.getPaddingRight(), toolbar.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(appbar);
    }
}

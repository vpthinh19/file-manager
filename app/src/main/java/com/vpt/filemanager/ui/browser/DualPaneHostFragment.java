package com.vpt.filemanager.ui.browser;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.FileOpener;
import com.vpt.filemanager.core.MimeTypes;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.domain.model.FileCategory;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.ui.browser.action.CreateAction;
import com.vpt.filemanager.ui.browser.action.ShareAction;
import com.vpt.filemanager.ui.browser.controller.BackPressController;
import com.vpt.filemanager.ui.browser.controller.BottomBarController;
import com.vpt.filemanager.ui.browser.controller.InsetsController;
import com.vpt.filemanager.ui.browser.controller.SelectionBarController;
import com.vpt.filemanager.ui.browser.controller.ToolbarController;
import com.vpt.filemanager.ui.editor.TextEditorActivity;

/**
 * Host của 2 PaneFragment + bottom toolbar + selection bar. Sau Phase R-5a, fragment chỉ còn
 * lifecycle + PaneController callback impl + click flow tạm thời (sẽ chuyển sang OpenerRegistry
 * ở Phase R-5b). UI orchestration phân tán xuống Controllers + Actions trong sub-packages
 * (controller/, action/, dialog/).
 *
 * <p>Pattern controller lifecycle: plain Java + manual release. {@code onViewCreated} new() +
 * attach() từng controller; {@code onDestroyView} nullify references để GC.
 */
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

    // Controllers + Actions (lifecycle aligned với view, nullify trong onDestroyView)
    private ToolbarController toolbarCtrl;
    private BottomBarController bottomBarCtrl;
    private SelectionBarController selectionBarCtrl;
    private InsetsController insetsCtrl;
    private BackPressController backPressCtrl;
    private CreateAction createAction;
    private ShareAction shareAction;

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

        // Order matters: Actions ctor first (Controllers reference them), Controllers second,
        // attach() last (calls binding listeners — all refs ready).
        createAction = new CreateAction(this);
        shareAction = new ShareAction(this);
        toolbarCtrl = new ToolbarController(this, binding);
        bottomBarCtrl = new BottomBarController(this, binding, createAction);
        selectionBarCtrl = new SelectionBarController(this, binding, shareAction);
        insetsCtrl = new InsetsController(binding);
        backPressCtrl = new BackPressController(this);

        toolbarCtrl.attach();
        bottomBarCtrl.attach();
        selectionBarCtrl.attach();
        insetsCtrl.attach();
        backPressCtrl.attach();

        observePane(PANE_LEFT, leftVm);
        observePane(PANE_RIGHT, rightVm);

        applyActivePaneVisual();
        syncFromActive();
    }

    @Override
    public void onDestroyView() {
        toolbarCtrl = null;
        bottomBarCtrl = null;
        selectionBarCtrl = null;
        insetsCtrl = null;
        backPressCtrl = null;
        createAction = null;
        shareAction = null;
        binding = null;
        super.onDestroyView();
    }

    // ───────────── PaneController callbacks ─────────────

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

        if (!node.isDirectory()
                && FileCategory.ofExtension(node.name()) == FileCategory.UNKNOWN) {
            showOpenAsDialog(node);
            return;
        }

        FileOpener.Action action = FileOpener.decide(node);
        switch (action) {
            case OPEN_TEXT:
                openAsText(node.path());
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

    // ───────────── Click flow (R-5b sẽ chuyển sang OpenerRegistry) ─────────────

    private void showOpenAsDialog(@NonNull FileNode node) {
        if (!node.path().isLocal()) {
            toast("Opening files inside archive: coming in Phase 2C");
            return;
        }
        OpenAsDialogFragment.newInstance(node.name())
                .setListener(choice -> handleOpenAs(node, choice))
                .show(getChildFragmentManager(), "open-as");
    }

    private void handleOpenAs(@NonNull FileNode node, @NonNull OpenAsDialogFragment.OpenAs choice) {
        switch (choice) {
            case TEXT:
                openAsText(node.path());
                break;
            case IMAGE:
                openWithMime(node.path(), "image/*");
                break;
            case VIDEO:
                openWithMime(node.path(), "video/*");
                break;
            case AUDIO:
                openWithMime(node.path(), "audio/*");
                break;
            case ARCHIVE:
                activeVm().openArchive(node.path());
                break;
        }
    }

    private void openAsText(@NonNull FilePath path) {
        if (!path.isLocal()) {
            toast("Editing inside archive: coming in Phase 2C");
            return;
        }
        Intent intent = new Intent(requireContext(), TextEditorActivity.class);
        intent.putExtra(TextEditorActivity.EXTRA_PATH, path.path());
        startActivity(intent);
    }

    private void openWith(@NonNull FileNode node) {
        openWithPath(node.path());
    }

    /** Public — gọi từ {@link SelectionBarController} (sub-package) cho OPEN_WITH action. */
    public void openWithPath(@NonNull FilePath path) {
        openWithMime(path, null);
    }

    /**
     * System "Open with" chooser. {@code mimeOverride} force MIME (dùng bởi OpenAsDialog cho file
     * extension-less); {@code null} = auto-detect từ tên file.
     */
    private void openWithMime(@NonNull FilePath path, @Nullable String mimeOverride) {
        if (!path.isLocal()) {
            toast(getString(R.string.unavailable));
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    new File(path.path()));
            String mime = mimeOverride != null ? mimeOverride : MimeTypes.detect(path.name());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.action_open_with)));
        } catch (IllegalArgumentException | ActivityNotFoundException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    // ───────────── Drawer-driven entry point ─────────────

    /**
     * Gọi từ MainActivity khi drawer Storage/Trash/... item được chọn — drive active pane
     * navigate sang path mới. Phase R-5b sẽ thay {@link FilePath} bằng {@code VirtualNode}.
     */
    public void navigateActivePaneTo(@NonNull FilePath path) {
        activeVm().navigateTo(path);
    }

    // ───────────── Observers + active pane sync ─────────────

    private void observePane(@NonNull String paneId, @NonNull PaneViewModel vm) {
        vm.uiState().observe(getViewLifecycleOwner(), state -> {
            if (paneId.equals(activePaneId) && !vm.isInSelectionMode() && toolbarCtrl != null) {
                toolbarCtrl.renderState(state);
            }
        });
        vm.selection().observe(getViewLifecycleOwner(), selection -> {
            if (!paneId.equals(activePaneId) || selectionBarCtrl == null) {
                return;
            }
            selectionBarCtrl.renderBars(selection, toolbarCtrl);
            // Leaving selection mode phải restore path-derived toolbar title; nếu không
            // string "N selected" stuck sau khi selection clear.
            if (selection == null || selection.isEmpty()) {
                toolbarCtrl.renderState(vm.uiState().getValue());
            }
        });
        vm.canGoBack().observe(getViewLifecycleOwner(), can -> {
            if (paneId.equals(activePaneId) && bottomBarCtrl != null) {
                bottomBarCtrl.applyNavButtonState(Boolean.TRUE.equals(can),
                        Boolean.TRUE.equals(vm.canGoForward().getValue()));
            }
        });
        vm.canGoForward().observe(getViewLifecycleOwner(), can -> {
            if (paneId.equals(activePaneId) && bottomBarCtrl != null) {
                bottomBarCtrl.applyNavButtonState(Boolean.TRUE.equals(vm.canGoBack().getValue()),
                        Boolean.TRUE.equals(can));
            }
        });
        vm.events().observe(getViewLifecycleOwner(), this::toast);
    }

    private void syncFromActive() {
        if (toolbarCtrl == null || selectionBarCtrl == null || bottomBarCtrl == null) {
            return;
        }
        PaneViewModel vm = activeVm();
        Set<FilePath> selection = vm.selection().getValue();
        if (selection != null && !selection.isEmpty()) {
            selectionBarCtrl.renderBars(selection, toolbarCtrl);
        } else {
            selectionBarCtrl.renderBars(null, toolbarCtrl);
            toolbarCtrl.renderState(vm.uiState().getValue());
        }
        bottomBarCtrl.applyNavButtonState(
                Boolean.TRUE.equals(vm.canGoBack().getValue()),
                Boolean.TRUE.equals(vm.canGoForward().getValue()));
    }

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

    // ───────────── Package-private host accessors (cho controllers/actions) ─────────────

    /** Active PaneViewModel — Controllers/Actions gọi để route action. */
    @NonNull
    public PaneViewModel activeVm() {
        return viewModelForPane(activePaneId);
    }

    /** Active pane id — BottomBarController dùng để toggle swap. */
    @NonNull
    public String activePaneId() {
        return activePaneId;
    }

    /** Toast helper — Controllers/Actions (sub-package) show error/info qua đây. */
    public void toast(@NonNull CharSequence message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}

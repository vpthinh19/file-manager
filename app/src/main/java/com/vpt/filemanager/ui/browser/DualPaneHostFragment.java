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

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.MimeTypes;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.domain.model.FileCategory;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.opener.FileOpener;
import com.vpt.filemanager.opener.OpenContext;
import com.vpt.filemanager.opener.OpenerRegistry;
import com.vpt.filemanager.opener.PaneNavigator;
import com.vpt.filemanager.ui.browser.action.CreateAction;
import com.vpt.filemanager.ui.browser.action.ShareAction;
import com.vpt.filemanager.ui.browser.controller.BackPressController;
import com.vpt.filemanager.ui.browser.controller.BottomBarController;
import com.vpt.filemanager.ui.browser.controller.InsetsController;
import com.vpt.filemanager.ui.browser.controller.SelectionBarController;
import com.vpt.filemanager.ui.browser.controller.ToolbarController;
import com.vpt.filemanager.ui.editor.TextEditorActivity;

/**
 * Host của 2 PaneFragment + bottom toolbar + selection bar. Phase R-5b: click flow migrated sang
 * {@link OpenerRegistry} (Strategy-per-file-type). UNKNOWN category vẫn fallback OpenAs dialog
 * cho parity với UX hiện tại. UI orchestration phân tán xuống Controllers + Actions từ R-5a.
 *
 * <p>Pattern controller lifecycle: plain Java + manual release.
 */
@AndroidEntryPoint
public final class DualPaneHostFragment extends Fragment implements PaneController {
    public static final String PANE_LEFT = "left";
    public static final String PANE_RIGHT = "right";

    private static final String TAG_PANE_LEFT = "pane_left";
    private static final String TAG_PANE_RIGHT = "pane_right";
    private static final String STATE_ACTIVE_PANE = "active_pane";

    @Inject
    OpenerRegistry openerRegistry;

    private FragmentDualPaneHostBinding binding;
    private PaneViewModel leftVm;
    private PaneViewModel rightVm;
    private String activePaneId = PANE_LEFT;

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
    public void onOpenFile(@NonNull String paneId, @NonNull VirtualNode node) {
        if (!paneId.equals(activePaneId)) {
            activePaneId = paneId;
            applyActivePaneVisual();
            syncFromActive();
        }
        // UNKNOWN category → OpenAs dialog (parity với UX hiện tại). Phase 2D có thể tích hợp
        // OpenAs vào OpenerRegistry khi Image/Video/AudioOpener wire xong.
        if (FileCategory.ofExtension(node.name()) == FileCategory.UNKNOWN) {
            showOpenAsDialog(node);
            return;
        }
        FileOpener opener = openerRegistry.openerFor(node);
        if (opener == null) {
            // Archive entry không có in-app opener — toast graceful.
            toast(getString(R.string.unavailable));
            return;
        }
        PaneViewModel vm = viewModelForPane(paneId);
        PaneNavigator nav = vm::navigateTo;
        OpenContext ctx = new OpenContext(requireContext(), getChildFragmentManager(), nav);
        try {
            opener.onOpen(node, ctx);
        } catch (NodeException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    // ───────────── OpenAs dialog (legacy UNKNOWN-extension fallback) ─────────────

    private void showOpenAsDialog(@NonNull VirtualNode node) {
        if (!node.path().isLocal()) {
            toast("Opening files inside archive: coming in Phase 2C");
            return;
        }
        OpenAsDialogFragment.newInstance(node.name())
                .setListener(choice -> handleOpenAs(node, choice))
                .show(getChildFragmentManager(), "open-as");
    }

    private void handleOpenAs(@NonNull VirtualNode node, @NonNull OpenAsDialogFragment.OpenAs choice) {
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

    /** Public — gọi từ {@link SelectionBarController} (sub-package) cho OPEN_WITH action. */
    public void openWithPath(@NonNull FilePath path) {
        openWithMime(path, null);
    }

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

    // ───────────── Package-public host accessors (cho controllers/actions sub-packages) ─────────────

    @NonNull
    public PaneViewModel activeVm() {
        return viewModelForPane(activePaneId);
    }

    @NonNull
    public String activePaneId() {
        return activePaneId;
    }

    public void toast(@NonNull CharSequence message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}

package com.vpt.filemanager.ui.pane;

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
import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

import com.vpt.filemanager.R;
import com.vpt.filemanager.threading.AppExecutors;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.format.FileCategory;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.opener.NodeOpener;
import com.vpt.filemanager.node.opener.OpenContext;
import com.vpt.filemanager.node.opener.OpenerRegistry;
import com.vpt.filemanager.node.NodeNavigator;
import com.vpt.filemanager.rules.WorkspaceRuleState;
import com.vpt.filemanager.ui.drawer.DrawerHost;
import com.vpt.filemanager.ui.pane.flow.CreateAction;
import com.vpt.filemanager.ui.pane.flow.ShareAction;
import com.vpt.filemanager.ui.pane.flow.TransferAction;
import com.vpt.filemanager.ui.pane.flow.TransferMode;
import com.vpt.filemanager.ui.pane.controller.BackPressController;
import com.vpt.filemanager.ui.pane.controller.BottomBarController;
import com.vpt.filemanager.ui.pane.controller.InsetsController;
import com.vpt.filemanager.ui.pane.controller.SelectionBarController;
import com.vpt.filemanager.ui.pane.controller.ToolbarController;
import com.vpt.filemanager.ui.dialog.OpenAsDialogFragment;
import com.vpt.filemanager.ui.editor.TextEditorActivity;
import com.vpt.filemanager.operations.openwith.OpenWithRequest;
import com.vpt.filemanager.operations.openwith.PrepareOpenWithRequestOperation;
import com.vpt.filemanager.workspace.WorkspaceStore;
import com.vpt.filemanager.workspace.WorkspaceAction;
import com.vpt.filemanager.workspace.WorkspaceCommandDispatcher;

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

    @Inject
    WorkspaceStore workspace;

    // Phase C-1b: deps cho TransferAction (cross-pane copy/move). Injected qua Hilt thay vì
    // resolve qua activeVm() để TransferAction là true stateless action giống CreateAction/Share.
    @Inject
    AppExecutors executors;

    @Inject
    NodeFactory nodeFactory;

    @Inject
    WorkspaceCommandDispatcher commands;

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
    private TransferAction transferAction;
    private final PrepareOpenWithRequestOperation prepareOpenWithRequestOperation =
            new PrepareOpenWithRequestOperation();

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

        createAction = new CreateAction(this, executors, nodeFactory, commands);
        shareAction = new ShareAction(this);
        transferAction = new TransferAction(this, executors, nodeFactory, commands);
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
        observeWorkspaceMutations();

        applyActivePaneVisual();
        syncFromActive();
    }

    /** Reconcile only panes whose live directory snapshot is affected by a mutation. */
    private void observeWorkspaceMutations() {
        workspace.mutations().observe(getViewLifecycleOwner(), mutation -> {
            if (mutation == null) {
                return;
            }
            leftVm.reconcile(mutation);
            rightVm.reconcile(mutation);
        });
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
        // Phase C-1b fix (Codex review): cancel in-flight batch trước khi null reference —
        // tránh IO thread hold stale Fragment ref + show ConflictDialog vào dead Activity.
        if (transferAction != null) {
            transferAction.cancel();
        }
        transferAction = null;
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
        // R-7b: pane swap có thể đổi scheme (vd left=trash, right=storage) → drawer highlight
        // phải re-sync. uiState observer cũng emit khi pane reload, nhưng pane swap không trigger
        // load mới → cần notify explicit ở đây.
        notifyDrawerHost();
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
        NodeOpener opener = openerRegistry.openerFor(node);
        if (opener == null) {
            // Archive entry không có in-app opener — toast graceful.
            toast(getString(R.string.unavailable));
            return;
        }
        PaneViewModel vm = viewModelForPane(paneId);
        NodeNavigator nav = vm::navigateTo;
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
                openWithNode(node, "image/*");
                break;
            case VIDEO:
                openWithNode(node, "video/*");
                break;
            case AUDIO:
                openWithNode(node, "audio/*");
                break;
            case ARCHIVE:
                activeVm().openArchive(node.path());
                break;
        }
    }

    private void openAsText(@NonNull NodePath path) {
        if (!path.isLocal()) {
            toast("Editing inside archive: coming in Phase 2C");
            return;
        }
        Intent intent = new Intent(requireContext(), TextEditorActivity.class);
        intent.putExtra(TextEditorActivity.EXTRA_PATH, path.path());
        startActivity(intent);
    }

    /** Public — gọi từ {@link SelectionBarController} (sub-package) cho OPEN_WITH action. */
    public void openWithPath(@NonNull NodePath path) {
        openWithMime(path, null);
    }

    private void openWithMime(@NonNull NodePath path, @Nullable String mimeOverride) {
        try {
            openWithNode(nodeFactory.fromPath(path), mimeOverride);
        } catch (NodeException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    private void openWithNode(@NonNull VirtualNode node, @Nullable String mimeOverride) {
        try {
            OpenWithRequest request = prepareOpenWithRequestOperation.execute(
                    new PrepareOpenWithRequestOperation.Input(node, mimeOverride));
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    new File(request.localPath.path()));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, request.mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.action_open_with)));
        } catch (NodeException | IllegalArgumentException | ActivityNotFoundException e) {
            toast(e.getMessage() == null ? getString(R.string.unavailable) : e.getMessage());
        }
    }

    // ───────────── Drawer-driven entry point ─────────────

    public void navigateActivePaneTo(@NonNull NodePath path) {
        activeVm().navigateTo(path);
    }

    // ───────────── Observers + active pane sync ─────────────

    private void observePane(@NonNull String paneId, @NonNull PaneViewModel vm) {
        vm.uiState().observe(getViewLifecycleOwner(), state -> {
            if (!paneId.equals(activePaneId)) {
                return;
            }
            if (!vm.isInSelectionMode() && toolbarCtrl != null) {
                toolbarCtrl.renderState(state);
            }
            if (bottomBarCtrl != null) {
                bottomBarCtrl.applyLocationState(vm.currentPath(), inactiveVm().currentPath());
            }
            // Phase R-7b: active pane đổi path → drawer highlight phải re-sync (Storage/Trash/
            // Bookmarks). uiState là tín hiệu reliable vì mọi navigate đều emit Loading→Content.
            notifyDrawerHost();
        });
        // Phase R-7a: observe selectionMode + selection riêng. EITHER fire → re-render bars.
        // selectionMode controls visibility; selection controls count + range enabled.
        vm.selectionMode().observe(getViewLifecycleOwner(), mode -> {
            if (paneId.equals(activePaneId)) {
                renderActiveBars(vm);
            }
        });
        vm.selection().observe(getViewLifecycleOwner(), selection -> {
            if (paneId.equals(activePaneId)) {
                renderActiveBars(vm);
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

    /**
     * Re-render selection bar + toolbar khi selectionMode hoặc selection thay đổi. Khi không
     * trong mode, restore toolbar về path-state.
     */
    private void renderActiveBars(@NonNull PaneViewModel vm) {
        if (selectionBarCtrl == null || toolbarCtrl == null) {
            return;
        }
        Boolean mode = vm.selectionMode().getValue();
        Set<NodePath> sel = vm.selection().getValue();
        selectionBarCtrl.renderBars(mode, sel, toolbarCtrl);
        if (!Boolean.TRUE.equals(mode)) {
            toolbarCtrl.renderState(vm.uiState().getValue());
        }
    }

    private void syncFromActive() {
        if (toolbarCtrl == null || selectionBarCtrl == null || bottomBarCtrl == null) {
            return;
        }
        PaneViewModel vm = activeVm();
        renderActiveBars(vm);
        bottomBarCtrl.applyNavButtonState(
                Boolean.TRUE.equals(vm.canGoBack().getValue()),
                Boolean.TRUE.equals(vm.canGoForward().getValue()));
        bottomBarCtrl.applyLocationState(vm.currentPath(), inactiveVm().currentPath());
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

    /**
     * Phase C-1b: cross-pane transfer cần "pane còn lại". MT Manager raison d'être của dual-pane
     * — source = active, destination = inactive. Cả 2 pane luôn loaded sau app start nên KHÔNG
     * có trường hợp inactive null; trả về VM trực tiếp.
     */
    @NonNull
    public PaneViewModel inactiveVm() {
        return viewModelForPane(PANE_LEFT.equals(activePaneId) ? PANE_RIGHT : PANE_LEFT);
    }

    @NonNull
    public String activePaneId() {
        return activePaneId;
    }

    @NonNull
    public EnumSet<WorkspaceAction> disabledActions(@NonNull WorkspaceRuleState state) {
        return commands.disabledActions(state);
    }

    /**
     * Phase C-1b entry point: SelectionBarController wire COPY/MOVE từ bottom sheet vào đây.
     * Thread: main. Delegate vào TransferAction để encapsulate logic IO + conflict bridge.
     */
    public void transferSelectionToOtherPane(@NonNull TransferMode mode) {
        if (transferAction != null) {
            transferAction.execute(mode);
        }
    }

    public void toast(@NonNull CharSequence message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Notify drawer host (Activity) refresh highlight theo active pane path. KISS — không cache
     * scheme, Activity tự đọc {@link #activeVm()} mỗi lần. Gọi sau mọi event làm path đổi
     * (uiState navigate / pane swap).
     */
    private void notifyDrawerHost() {
        if (requireActivity() instanceof DrawerHost dh) {
            dh.syncDrawerSelection();
        }
    }
}

package com.vpt.filemanager.browser.ui.pane;

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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

import com.vpt.filemanager.R;
import com.vpt.filemanager.browser.action.Action;
import com.vpt.filemanager.browser.action.ActionKey;
import com.vpt.filemanager.browser.action.browse.NavigateAction;
import com.vpt.filemanager.browser.action.entry.CreateEntryAction;
import com.vpt.filemanager.browser.action.entry.ExistingNamePolicy;
import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;
import com.vpt.filemanager.browser.item.Path;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.ui.dialog.ConflictDialog;
import com.vpt.filemanager.browser.ui.drawer.DrawerHost;
import com.vpt.filemanager.texteditor.ui.TextEditorActivity;
import com.vpt.filemanager.browser.ui.format.MimeTypes;
import com.vpt.filemanager.browser.ui.pane.controller.BackPressController;
import com.vpt.filemanager.browser.ui.pane.controller.BottomBarController;
import com.vpt.filemanager.browser.ui.pane.controller.InsetsController;
import com.vpt.filemanager.browser.ui.pane.controller.SelectionBarController;
import com.vpt.filemanager.browser.ui.pane.controller.ToolbarController;
import com.vpt.filemanager.browser.ui.properties.PropertiesDialogFragment;
import com.vpt.filemanager.browser.workspace.WorkspaceCoordinator;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.browser.workspace.state.PaneState;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;
import com.vpt.filemanager.media.image.ImageViewerActivity;
import com.vpt.filemanager.media.playback.MediaPlayerActivity;

/** Android surface for dual panes. Business decisions are dispatched to WorkspaceCoordinator. */
@AndroidEntryPoint
public final class DualPaneHostFragment extends Fragment implements PaneController {
    public static final String PANE_LEFT = "left";
    public static final String PANE_RIGHT = "right";
    private static final String TAG_LEFT = "pane_left";
    private static final String TAG_RIGHT = "pane_right";

    private FragmentDualPaneHostBinding binding;
    private WorkspaceCoordinator workspace;
    private ToolbarController toolbar;
    private BottomBarController bottomBar;
    private SelectionBarController selectionBar;

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        workspace = new ViewModelProvider(this).get(WorkspaceCoordinator.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_dual_pane_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        binding = FragmentDualPaneHostBinding.bind(view);
        if (state == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.pane_left_container, PaneFragment.newInstance(PANE_LEFT), TAG_LEFT)
                    .replace(R.id.pane_right_container, PaneFragment.newInstance(PANE_RIGHT), TAG_RIGHT)
                    .commitNow();
        }
        toolbar = new ToolbarController(this, binding);
        bottomBar = new BottomBarController(this, binding);
        selectionBar = new SelectionBarController(this, binding);
        toolbar.attach();
        bottomBar.attach();
        selectionBar.attach();
        new InsetsController(binding).attach();
        new BackPressController(this).attach();
        workspace.state().observe(getViewLifecycleOwner(), this::render);
        workspace.effects().observe(getViewLifecycleOwner(), this::applyEffect);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        toolbar = null;
        bottomBar = null;
        selectionBar = null;
        super.onDestroyView();
    }

    @NonNull @Override
    public LiveData<WorkspaceSnapshot> workspaceState() {
        return workspace.state();
    }

    @Override
    public void dispatch(@NonNull Action action) {
        workspace.dispatch(action);
    }

    public PaneId activePaneId() { return workspace.activePane(); }
    public PaneState activeState() { return workspace.current().active(); }
    public PaneState inactiveState() { return workspace.current().inactive(); }
    public EnumSet<ActionKey> disabledActions() { return workspace.disabledActions(); }

    public void navigateActivePaneTo(@NonNull Path target) {
        dispatch(new NavigateAction(activePaneId(), target));
    }

    public void toast(@NonNull CharSequence text) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show();
    }

    private void render(WorkspaceSnapshot state) {
        PaneState active = state.active();
        toolbar.renderState(active);
        bottomBar.render(active, state.inactive(), disabledActions());
        selectionBar.render(active, toolbar);
        PaneFragment left = (PaneFragment) getChildFragmentManager().findFragmentByTag(TAG_LEFT);
        PaneFragment right = (PaneFragment) getChildFragmentManager().findFragmentByTag(TAG_RIGHT);
        if (left != null) left.setPaneActivated(state.activePane == PaneId.LEFT);
        if (right != null) right.setPaneActivated(state.activePane == PaneId.RIGHT);
        if (requireActivity() instanceof DrawerHost drawer) drawer.syncDrawerSelection();
    }

    private void applyEffect(WorkspaceEffect effect) {
        if (effect instanceof WorkspaceEffect.Toast value) {
            toast(value.message());
        } else if (effect instanceof WorkspaceEffect.OpenText value) {
            startActivity(new Intent(requireContext(), TextEditorActivity.class)
                    .putExtra(TextEditorActivity.EXTRA_PATH, value.path())
                    .putExtra(TextEditorActivity.EXTRA_DISPLAY_NAME, value.displayName())
                    .putExtra(TextEditorActivity.EXTRA_READ_ONLY, value.readOnly())
                    .putExtra(TextEditorActivity.EXTRA_ARCHIVE_ENTRY, value.archiveEntry()));
        } else if (effect instanceof WorkspaceEffect.OpenImage value) {
            startActivity(new Intent(requireContext(), ImageViewerActivity.class)
                    .putExtra(ImageViewerActivity.EXTRA_PATH, value.path()));
        } else if (effect instanceof WorkspaceEffect.OpenMedia value) {
            startActivity(new Intent(requireContext(), MediaPlayerActivity.class)
                    .putExtra(MediaPlayerActivity.EXTRA_PATH, value.path())
                    .putExtra(MediaPlayerActivity.EXTRA_VIDEO, value.video()));
        } else if (effect instanceof WorkspaceEffect.OpenExternal value) {
            openExternal(value.item());
        } else if (effect instanceof WorkspaceEffect.ShowProperties value) {
            PropertiesDialogFragment.newInstance(value.item().localPath())
                    .show(getChildFragmentManager(), "properties");
        } else if (effect instanceof WorkspaceEffect.Share value) {
            share(value.items());
        } else if (effect instanceof WorkspaceEffect.ResolveCreateConflict value) {
            ConflictDialog.show(requireContext(), value.name(), new ConflictDialog.OnChoice() {
                @Override public void onReplace() {
                    CreateEntryAction initial = value.action();
                    dispatch(new CreateEntryAction(initial.pane(), initial.type(), initial.name(),
                            ExistingNamePolicy.REPLACE));
                }

                @Override public void onKeepBoth() {
                    CreateEntryAction initial = value.action();
                    dispatch(new CreateEntryAction(initial.pane(), initial.type(), initial.name(),
                            ExistingNamePolicy.KEEP_BOTH));
                }
            });
        }
    }

    private void openExternal(Item item) {
        if (!item.isLocalActionTarget() || item.isFolder()) {
            toast(getString(R.string.unavailable));
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", new File(item.localPath()));
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, MimeTypes.detect(item.name()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.action_open_with)));
        } catch (ActivityNotFoundException | IllegalArgumentException error) {
            toast(getString(R.string.unavailable));
        }
    }

    private void share(List<Item> items) {
        ArrayList<Uri> uris = new ArrayList<>();
        String authority = requireContext().getPackageName() + ".fileprovider";
        for (Item item : items) {
            if (!item.isLocalActionTarget() || item.isFolder()) continue;
            try {
                uris.add(FileProvider.getUriForFile(requireContext(), authority,
                        new File(item.localPath())));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (uris.isEmpty()) {
            toast(getString(R.string.unavailable));
            return;
        }
        Intent intent = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE)
                .setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (uris.size() == 1) intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
        } catch (ActivityNotFoundException error) {
            toast(getString(R.string.unavailable));
        }
    }
}

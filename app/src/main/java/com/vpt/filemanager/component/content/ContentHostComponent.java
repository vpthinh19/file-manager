package com.vpt.filemanager.component.content;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.component.drawer.DrawerComponent;
import com.vpt.filemanager.core.format.MimeTypes;
import com.vpt.filemanager.component.content.editor.TextEditorFragment;
import com.vpt.filemanager.state.StateViewModel;

/** Swaps the browser surface for exactly one resolved full-screen content component. */
public final class ContentHostComponent {
    private static final String TAG = "full-screen-content";
    private static final long TRANSITION_MILLIS = 180L;
    private final AppCompatActivity activity;
    private final StateViewModel state;
    private final DrawerComponent drawer;
    private final View browser;
    private final View host;
    private OpenedContent shown;

    public ContentHostComponent(AppCompatActivity activity, StateViewModel state, DrawerComponent drawer) {
        this.activity = activity;
        this.state = state;
        this.drawer = drawer;
        browser = activity.findViewById(R.id.browser_content);
        host = activity.findViewById(R.id.content_container);
    }

    public void attach(LifecycleOwner owner) {
        state.content().observe(owner, this::render);
    }

    public boolean onBackPressed() {
        Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(TAG);
        return fragment instanceof FullScreenContent content && content.onBackPressed();
    }

    private void render(OpenedContent content) {
        if (content == null) {
            shown = null;
            drawer.setLocked(false);
            hideContent();
            return;
        }
        if (content.type() == ContentType.OTHER) {
            openExternal(content);
            state.back(content.pane());
            return;
        }
        if (content.equals(shown)) return;
        boolean reveal = host.getVisibility() != View.VISIBLE;
        shown = content;
        host.setVisibility(View.VISIBLE);
        drawer.setLocked(true);
        Fragment fragment = switch (content.type()) {
            case TEXT -> TextEditorFragment.newInstance(content);
            case IMAGE -> ImageContentFragment.newInstance(content.localPath(), content.displayName());
            case AUDIO -> MediaContentFragment.newInstance(content.localPath(), content.displayName(), false);
            case VIDEO -> MediaContentFragment.newInstance(content.localPath(), content.displayName(), true);
            case OTHER -> throw new IllegalStateException();
        };
        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_container, fragment, TAG).commit();
        if (reveal) revealContent();
    }

    private void revealContent() {
        browser.animate().cancel();
        host.animate().cancel();
        browser.setVisibility(View.VISIBLE);
        browser.setAlpha(1f);
        host.setAlpha(0f);
        host.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        browser.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        host.animate().alpha(1f).setDuration(TRANSITION_MILLIS)
                .withEndAction(() -> host.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        browser.animate().alpha(0f).setDuration(TRANSITION_MILLIS).withEndAction(() -> {
            if (shown != null) browser.setVisibility(View.GONE);
            browser.setAlpha(1f);
            browser.setLayerType(View.LAYER_TYPE_NONE, null);
        }).start();
    }

    private void hideContent() {
        Fragment present = activity.getSupportFragmentManager().findFragmentByTag(TAG);
        if (host.getVisibility() != View.VISIBLE) {
            browser.setVisibility(View.VISIBLE);
            if (present != null) activity.getSupportFragmentManager().beginTransaction()
                    .remove(present).commitAllowingStateLoss();
            return;
        }
        browser.animate().cancel();
        host.animate().cancel();
        browser.setVisibility(View.VISIBLE);
        browser.setAlpha(0f);
        browser.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        host.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        browser.animate().alpha(1f).setDuration(TRANSITION_MILLIS)
                .withEndAction(() -> browser.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        host.animate().alpha(0f).setDuration(TRANSITION_MILLIS).withEndAction(() -> {
            host.setLayerType(View.LAYER_TYPE_NONE, null);
            if (shown != null) return;
            host.setVisibility(View.GONE);
            host.setAlpha(1f);
            if (present != null) activity.getSupportFragmentManager().beginTransaction()
                    .remove(present).commitAllowingStateLoss();
        }).start();
    }

    private void openExternal(OpenedContent content) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(content.contentUri()), MimeTypes.detect(content.displayName()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.action_open_with)));
        } catch (ActivityNotFoundException | IllegalArgumentException error) {
            Toast.makeText(activity, R.string.unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}

package com.vpt.filemanager.component.content;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.component.drawer.DrawerComponent;
import com.vpt.filemanager.core.format.MimeType;
import com.vpt.filemanager.component.content.editor.TextEditorFragment;
import com.vpt.filemanager.state.StateViewModel;

/** Swaps the browser surface for exactly one resolved full-screen content component. */
public final class ContentHostComponent {
    private static final String TAG = "full-screen-content";
    private static final long ENTER_TRANSITION_MILLIS = 360L;
    private static final long EXIT_TRANSITION_MILLIS = 320L;
    private static final float CONTENT_ENTER_SCALE = 0.985f;
    private static final float BROWSER_EXIT_SCALE = 0.99f;
    private static final PathInterpolator ENTER_EASING =
            new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final PathInterpolator EXIT_EASING =
            new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private final AppCompatActivity activity;
    private final StateViewModel state;
    private final DrawerComponent drawer;
    private final View browser;
    private final View host;
    private OpenedContent shown;
    private boolean transitioningOut;

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
        boolean reveal = host.getVisibility() != View.VISIBLE || transitioningOut;
        transitioningOut = false;
        shown = content;
        drawer.setLocked(true);
        if (reveal) prepareReveal();
        else host.setVisibility(View.VISIBLE);
        Fragment fragment = switch (content.type()) {
            case TEXT -> TextEditorFragment.newInstance(content);
            case IMAGE -> ImageContentFragment.newInstance(content.localPath(), content.displayName());
            case AUDIO -> MediaContentFragment.newInstance(content.localPath(), content.displayName(), false);
            case VIDEO -> MediaContentFragment.newInstance(content.localPath(), content.displayName(), true);
            case OTHER -> throw new IllegalStateException();
        };
        activity.getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.content_container, fragment, TAG).commit();
        if (reveal) host.post(() -> {
            if (content.equals(shown) && host.getVisibility() == View.VISIBLE) revealContent();
        });
    }

    private void prepareReveal() {
        cancelTransitions();
        resetSurface(browser);
        resetSurface(host);
        browser.setVisibility(View.VISIBLE);
        host.setVisibility(View.VISIBLE);
        host.setAlpha(0f);
        host.setScaleX(CONTENT_ENTER_SCALE);
        host.setScaleY(CONTENT_ENTER_SCALE);
    }

    private void revealContent() {
        if (shown == null) return;
        browser.animate().cancel();
        host.animate().cancel();
        host.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        browser.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        host.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(ENTER_TRANSITION_MILLIS).setInterpolator(ENTER_EASING)
                  .withEndAction(() -> host.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        browser.animate().alpha(0f).scaleX(BROWSER_EXIT_SCALE).scaleY(BROWSER_EXIT_SCALE)
                .setDuration(ENTER_TRANSITION_MILLIS).setInterpolator(ENTER_EASING)
                  .withEndAction(() -> {
              if (shown != null) browser.setVisibility(View.GONE);
              resetSurface(browser);
          }).start();
    }

    private void hideContent() {
        Fragment present = activity.getSupportFragmentManager().findFragmentByTag(TAG);
        if (host.getVisibility() != View.VISIBLE) {
            transitioningOut = false;
            browser.setVisibility(View.VISIBLE);
            resetSurface(browser);
            resetSurface(host);
            if (present != null) activity.getSupportFragmentManager().beginTransaction()
                      .remove(present).commitAllowingStateLoss();
            return;
        }
        transitioningOut = true;
        cancelTransitions();
        resetSurface(browser);
        resetSurface(host);
        browser.setVisibility(View.VISIBLE);
        browser.setAlpha(0f);
        browser.setScaleX(BROWSER_EXIT_SCALE);
        browser.setScaleY(BROWSER_EXIT_SCALE);
        browser.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        host.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        browser.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(EXIT_TRANSITION_MILLIS).setInterpolator(EXIT_EASING)
                  .withEndAction(() -> browser.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        host.animate().alpha(0f).scaleX(CONTENT_ENTER_SCALE).scaleY(CONTENT_ENTER_SCALE)
                .setDuration(EXIT_TRANSITION_MILLIS).setInterpolator(EXIT_EASING)
                  .withEndAction(() -> {
              resetSurface(host);
              if (shown != null) return;
              transitioningOut = false;
              host.setVisibility(View.GONE);
              if (present != null) activity.getSupportFragmentManager().beginTransaction()
                      .remove(present).commitAllowingStateLoss();
          }).start();
    }

    private void cancelTransitions() {
        browser.animate().cancel();
        host.animate().cancel();
    }

    private static void resetSurface(View surface) {
        surface.setAlpha(1f);
        surface.setScaleX(1f);
        surface.setScaleY(1f);
        surface.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    private void openExternal(OpenedContent content) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(content.contentUri()), MimeType.detect(content.displayName()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.action_open_with)));
        } catch (ActivityNotFoundException | IllegalArgumentException error) {
            Toast.makeText(activity, R.string.unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}

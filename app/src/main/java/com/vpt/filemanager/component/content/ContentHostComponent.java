package com.vpt.filemanager.component.content;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;

import com.vpt.filemanager.R;
import com.vpt.filemanager.core.detect.ContentType;
import com.vpt.filemanager.component.drawer.DrawerComponent;
import com.vpt.filemanager.core.format.MimeTypes;
import com.vpt.filemanager.component.content.editor.TextEditorFragment;
import com.vpt.filemanager.component.state.StateViewModel;

import java.io.File;

/** Swaps the browser surface for exactly one resolved full-screen content component. */
public final class ContentHostComponent {
    private static final String TAG = "full-screen-content";
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
            host.setVisibility(View.GONE);
            browser.setVisibility(View.VISIBLE);
            drawer.setLocked(false);
            Fragment present = activity.getSupportFragmentManager().findFragmentByTag(TAG);
            if (present != null) activity.getSupportFragmentManager().beginTransaction()
                    .remove(present).commitAllowingStateLoss();
            return;
        }
        if (content.type() == ContentType.OTHER) {
            openExternal(content);
            state.back(content.pane());
            return;
        }
        if (content.equals(shown)) return;
        shown = content;
        browser.setVisibility(View.GONE);
        host.setVisibility(View.VISIBLE);
        drawer.setLocked(true);
        Fragment fragment = switch (content.type()) {
            case TEXT -> TextEditorFragment.newInstance(content);
            case IMAGE -> ImageContentFragment.newInstance(content.localPath());
            case AUDIO -> MediaContentFragment.newInstance(content.localPath(), false);
            case VIDEO -> MediaContentFragment.newInstance(content.localPath(), true);
            case OTHER -> throw new IllegalStateException();
        };
        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_container, fragment, TAG).commit();
    }

    private void openExternal(OpenedContent content) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", new File(content.localPath()));
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, MimeTypes.detect(content.displayName()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.action_open_with)));
        } catch (ActivityNotFoundException | IllegalArgumentException error) {
            Toast.makeText(activity, R.string.unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}

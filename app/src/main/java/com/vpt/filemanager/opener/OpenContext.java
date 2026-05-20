package com.vpt.filemanager.opener;

import java.util.Objects;

import android.content.Context;

import androidx.fragment.app.FragmentManager;

/**
 * Bag chứa context để {@link FileOpener#onOpen} thực thi action. Caller (BrowserFragment ở R-5)
 * tạo instance khi user click node + truyền cho opener đã match.
 *
 * <p>Cố ý KHÔNG expose Fragment class — opener không cần biết là Fragment hay Activity host,
 * chỉ cần {@link Context} để launch Intent + {@link FragmentManager} để show dialog +
 * {@link PaneNavigator} để navigate pane. Test mock dễ hơn, refactor host không đụng opener.
 */
public final class OpenContext {
    private final Context context;
    private final FragmentManager fragmentManager;
    private final PaneNavigator pane;

    public OpenContext(Context context, FragmentManager fragmentManager, PaneNavigator pane) {
        this.context = Objects.requireNonNull(context, "context");
        this.fragmentManager = Objects.requireNonNull(fragmentManager, "fragmentManager");
        this.pane = Objects.requireNonNull(pane, "pane");
    }

    public Context context() {
        return context;
    }

    public FragmentManager fragmentManager() {
        return fragmentManager;
    }

    public PaneNavigator pane() {
        return pane;
    }
}

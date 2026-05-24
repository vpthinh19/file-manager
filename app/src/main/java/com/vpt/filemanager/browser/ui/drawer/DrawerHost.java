package com.vpt.filemanager.browser.ui.drawer;

/** Operations exposed by the activity-owned navigation drawer to pane UI. */
public interface DrawerHost {
    void openDrawer();

    void closeDrawer();

    boolean isDrawerOpen();

    void syncDrawerSelection();
}

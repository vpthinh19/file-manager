package com.vpt.filemanager.ui.drawer;

/**
 * Contract implemented by the hosting Activity for the navigation drawer. Fragments use this to
 * open / close / inspect drawer state without holding a {@code DrawerLayout} reference — keeps
 * the drawer wiring centralised in the Activity while letting Fragments coordinate back-press
 * handling with the rest of their UI (selection mode, navigateUp, etc.).
 */
public interface DrawerHost {
    void openDrawer();

    void closeDrawer();

    boolean isDrawerOpen();
}

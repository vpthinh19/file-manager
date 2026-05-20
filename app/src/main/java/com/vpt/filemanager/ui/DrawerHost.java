package com.vpt.filemanager.ui;

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

    /**
     * Đồng bộ highlight drawer theo trạng thái active pane hiện tại. Phase R-7b: Trash + Bookmark
     * giờ navigate qua pane (không còn Fragment riêng) → host Fragment phải kích hoạt sync khi
     * active pane đổi path hoặc khi user swap pane left↔right.
     */
    void syncDrawerSelection();
}

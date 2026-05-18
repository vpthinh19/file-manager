package com.vpt.filemanager.ui.drawer;

/**
 * Sink for drawer navigation events. Implemented by the foreground Fragment hosted in
 * {@code R.id.fragment_container} (currently {@code DualPaneHostFragment}); the Activity dispatches
 * NavigationView item clicks here so the Fragment can react with its own ViewModels.
 *
 * <p>Add a new method per drawer entry — keeping the interface narrow and verb-named makes the
 * implementor obvious about what state changes.
 */
public interface DrawerActionHandler {
    void onTrashSelected();

    void onBookmarksSelected();

    void onSettingsSelected();
}

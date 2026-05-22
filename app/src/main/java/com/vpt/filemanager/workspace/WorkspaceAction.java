package com.vpt.filemanager.workspace;

/**
 * Concept-level actions available in a browser workspace.
 *
 * <p>This enum deliberately lives outside UI packages. Menus, toolbars, and future drawers can map
 * their own visual items to these actions, while constraint rules stay independent from any
 * particular component.
 */
public enum WorkspaceAction {
    COPY,
    MOVE,
    DELETE,
    RENAME,
    TOOLS,
    COMPRESS,
    PROPERTIES,
    SHARE,
    OPEN_WITH,
    BOOKMARK
}

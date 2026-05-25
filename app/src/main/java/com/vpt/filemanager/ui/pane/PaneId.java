package com.vpt.filemanager.ui.pane;

public enum PaneId {
    LEFT,
    RIGHT;

    public PaneId other() {
        return this == LEFT ? RIGHT : LEFT;
    }
}

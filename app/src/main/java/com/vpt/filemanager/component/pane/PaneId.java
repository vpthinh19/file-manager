package com.vpt.filemanager.component.pane;

public enum PaneId {
    LEFT,
    RIGHT;

    public PaneId other() {
        return this == LEFT ? RIGHT : LEFT;
    }
}

package com.vpt.filemanager.browser.workspace.state;

public enum PaneId {
    LEFT,
    RIGHT;

    public PaneId other() {
        return this == LEFT ? RIGHT : LEFT;
    }
}

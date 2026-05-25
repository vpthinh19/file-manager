package com.vpt.filemanager.state;

public enum PaneId {
    LEFT,
    RIGHT;

    public PaneId other() {
        return this == LEFT ? RIGHT : LEFT;
    }
}

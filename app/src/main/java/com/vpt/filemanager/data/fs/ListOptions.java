package com.vpt.filemanager.data.fs;

public final class ListOptions {
    public static final ListOptions DEFAULT = new ListOptions(true);

    private final boolean showHidden;

    public ListOptions(boolean showHidden) {
        this.showHidden = showHidden;
    }

    public boolean showHidden() {
        return showHidden;
    }
}

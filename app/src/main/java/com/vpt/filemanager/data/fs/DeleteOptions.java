package com.vpt.filemanager.data.fs;

public final class DeleteOptions {
    public static final DeleteOptions PERMANENT = new DeleteOptions(true);
    public static final DeleteOptions TRASH = new DeleteOptions(false);

    private final boolean permanent;

    public DeleteOptions(boolean permanent) {
        this.permanent = permanent;
    }

    public boolean permanent() {
        return permanent;
    }
}


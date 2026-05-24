package com.vpt.filemanager.core.error;

public final class NameConflictException extends FileOperationException {
    private final String name;
    public NameConflictException(String name) {
        super("Name already exists: " + name);
        this.name = name;
    }
    public String name() { return name; }
}

package com.vpt.filemanager.core.io;

import com.vpt.filemanager.domain.model.FileCategory;
import com.vpt.filemanager.domain.model.FileNode;

/**
 * Routes a {@link FileNode} to the right viewer action by inspecting its {@link FileCategory}.
 *
 * <p>Categorisation lives entirely in {@link FileCategory#ofExtension(String)} — this class is a
 * thin dispatcher so we keep a single source of truth for "what kind of file is this".
 */
public final class FileOpener {
    private FileOpener() {
    }

    public enum Action { OPEN_IMAGE, OPEN_VIDEO, OPEN_AUDIO, OPEN_TEXT, OPEN_ARCHIVE, OPEN_WITH }

    public static Action decide(FileNode node) {
        if (node == null || node.isDirectory()) {
            return Action.OPEN_WITH;
        }
        switch (FileCategory.ofExtension(node.name())) {
            case TEXT:
            case CODE:
                return Action.OPEN_TEXT;
            case IMAGE:
                return Action.OPEN_IMAGE;
            case VIDEO:
                return Action.OPEN_VIDEO;
            case AUDIO:
                return Action.OPEN_AUDIO;
            case ARCHIVE:
                return Action.OPEN_ARCHIVE;
            case PDF:
            case DOC:
            case APK:
            case UNKNOWN:
            default:
                return Action.OPEN_WITH;
        }
    }
}

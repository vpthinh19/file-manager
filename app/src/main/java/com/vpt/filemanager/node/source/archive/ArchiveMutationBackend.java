package com.vpt.filemanager.node.source.archive;

import java.nio.file.Path;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;

/**
 * Write boundary for an archive container.
 *
 * <p>The source layer supplies virtual entry paths; the backend owns archive rewrite and
 * replacement of the physical container. A future libarchive backend can implement this
 * contract without changing operations or pane code.
 */
public interface ArchiveMutationBackend {
    void createFile(NodePath archiveFile, String innerPath) throws NodeException;

    void createFolder(NodePath archiveFile, String innerPath) throws NodeException;

    void replaceFile(NodePath archiveFile, String innerPath, Path payload) throws NodeException;

    void rename(NodePath archiveFile, String fromInnerPath, String toInnerPath, boolean folder)
            throws NodeException;

    void delete(NodePath archiveFile, String innerPath, boolean folder) throws NodeException;
}

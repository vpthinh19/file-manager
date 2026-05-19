package com.vpt.filemanager.domain.model;

/**
 * Composite-pattern component for the file-system tree.
 *
 * <p>Subclasses cover every kind of "thing the browser can show in a row":
 * <ul>
 *   <li>{@code LocalFileNode} — a file or directory on the local FS</li>
 *   <li>{@code ArchiveNode} — an entry inside an archive (ZIP/TAR/...)</li>
 *   <li>{@code ParentFileNode} — the synthetic ".." row used to navigate up</li>
 * </ul>
 *
 * <p>The pattern is intentionally <em>lazy</em>: nodes never hold their children eagerly. A
 * directory's contents are fetched on demand through {@link
 * com.vpt.filemanager.domain.repository.FileRepository#list(FilePath)} so that opening
 * {@code /storage/emulated/0} doesn't materialise hundreds of thousands of {@code FileNode}
 * instances. Tree walks (e.g. {@code FolderSizeCalculator}) are the Visitor side of the pattern —
 * they iterate {@code FileNode}s returned by the repository rather than calling {@code children()}
 * on the node itself.
 *
 * <p>Equality + hashCode are derived from {@link #path()} so a node can be used directly as a
 * {@code Set}/{@code Map} key when tracking selection or visibility.
 */
public abstract class FileNode {
    public abstract FilePath path();

    public abstract String name();

    public abstract boolean isDirectory();

    public abstract boolean isSymbolicLink();

    public abstract long sizeBytes();

    public abstract long lastModifiedMillis();

    public abstract FileMetadata metadata();

    @Override
    public boolean equals(Object o) {
        return o instanceof FileNode && path().equals(((FileNode) o).path());
    }

    @Override
    public int hashCode() {
        return path().hashCode();
    }
}

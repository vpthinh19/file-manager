package com.vpt.filemanager.data.fs.local;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import com.vpt.filemanager.core.util.MimeTypes;
import com.vpt.filemanager.domain.model.FileMetadata;
import com.vpt.filemanager.domain.model.FileNode;
import com.vpt.filemanager.domain.model.FilePath;

/**
 * Local-filesystem {@link FileNode}. Eagerly snapshots type/size/mtime via a single
 * {@link Files#readAttributes(Path, Class, LinkOption...)} call at construction so that subsequent
 * accessor calls (the hot path during RecyclerView binding) do NOT incur a fresh stat syscall per
 * field — previously {@code isDirectory()}, {@code sizeBytes()}, {@code lastModifiedMillis()} each
 * triggered a separate stat, which on SD / network storage stalled scroll.
 *
 * <p>Safe because nodes are recreated whenever the directory is re-listed (after CRUD ops via
 * {@code refresh()}), so the cached attributes can't drift while the row is visible.
 */
public final class LocalFileNode extends FileNode {
    private final File file;
    private final FilePath path;
    private final boolean directory;
    private final boolean symbolicLink;
    private final long sizeBytes;
    private final long lastModifiedMillis;
    private FileMetadata metadata;

    public LocalFileNode(File file) {
        this.file = file;
        this.path = FilePath.local(file.getAbsolutePath());
        // One stat call instead of three. NOFOLLOW_LINKS so symlink kind is reflected on the link
        // itself, not its target — important for correct symbolicLink reporting.
        boolean isDir = false;
        boolean isLink = false;
        long size = 0L;
        long mtime = 0L;
        try {
            BasicFileAttributes attrs = Files.readAttributes(
                    file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            isDir = attrs.isDirectory();
            isLink = attrs.isSymbolicLink();
            size = attrs.size();
            mtime = attrs.lastModifiedTime().toMillis();
        } catch (IOException | RuntimeException ignored) {
            // File vanished or unreadable between list() and ctor — leave defaults; the row will
            // render as a zero-byte regular file with mtime=0 ("Parent" meta) and the next refresh
            // will clean it up.
        }
        this.directory = isDir;
        this.symbolicLink = isLink;
        // Directory size is conventionally surfaced as -1 ("Unknown") so the UI can render "Folder"
        // instead of a bogus byte count.
        this.sizeBytes = isDir ? -1L : size;
        this.lastModifiedMillis = mtime;
    }

    @Override
    public FilePath path() {
        return path;
    }

    @Override
    public String name() {
        String name = file.getName();
        return name.isEmpty() ? file.getAbsolutePath() : name;
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public boolean isSymbolicLink() {
        return symbolicLink;
    }

    @Override
    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public long lastModifiedMillis() {
        return lastModifiedMillis;
    }

    @Override
    public FileMetadata metadata() {
        if (metadata == null) {
            metadata = FileMetadata.builder()
                    .sizeBytes(sizeBytes)
                    .lastModifiedMillis(lastModifiedMillis)
                    .mimeType(MimeTypes.detect(name()))
                    .build();
        }
        return metadata;
    }
}

package com.vpt.filemanager.domain.model;

import java.util.Comparator;
import java.util.Locale;

import androidx.annotation.NonNull;

/**
 * Sort strategies for the browser list. Implemented as an enum-Strategy (Effective Java Item 34):
 * each constant carries its own {@link Comparator}, so callers route through
 * {@link #folderFirstComparator()} without a switch.
 *
 * <p>Folders are always grouped before files regardless of the user-selected order — a hard MT
 * Manager parity expectation. The wrapper inverts {@code isDirectory()} (true sorts last in
 * natural order, so {@code reversed()} puts directories first), then chains the per-strategy
 * comparator.
 */
public enum SortOrder {
    NAME_ASC(Comparator.comparing(n -> safe(n.name()))),
    NAME_DESC(Comparator.comparing((FileNode n) -> safe(n.name())).reversed()),
    SIZE_DESC(Comparator.comparingLong(FileNode::sizeBytes).reversed()),
    SIZE_ASC(Comparator.comparingLong(FileNode::sizeBytes)),
    DATE_DESC(Comparator.comparingLong(FileNode::lastModifiedMillis).reversed()),
    DATE_ASC(Comparator.comparingLong(FileNode::lastModifiedMillis)),
    TYPE(Comparator
            .comparing((FileNode n) -> extension(n.name()))
            .thenComparing(n -> safe(n.name())));

    public static final SortOrder DEFAULT = NAME_ASC;

    private final Comparator<FileNode> base;

    SortOrder(Comparator<FileNode> base) {
        this.base = base;
    }

    /**
     * The full comparator the browser should apply: directories first, then this strategy.
     */
    @NonNull
    public Comparator<FileNode> folderFirstComparator() {
        return Comparator.comparing(FileNode::isDirectory).reversed().thenComparing(base);
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }

    private static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1
                ? ""
                : name.substring(dot + 1).toLowerCase(Locale.US);
    }

    @NonNull
    public static SortOrder safeValueOf(String name) {
        if (name == null) return DEFAULT;
        try {
            return SortOrder.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}

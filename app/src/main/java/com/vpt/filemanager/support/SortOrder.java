package com.vpt.filemanager.support;

import java.util.Comparator;
import java.util.Locale;

import androidx.annotation.NonNull;

import com.vpt.filemanager.node.VirtualNode;

/**
 * Sort strategies for the browser list. Implemented as an enum-Strategy (Effective Java Item 34):
 * each constant carries its own {@link Comparator}, so callers route through
 * {@link #folderFirstComparator()} without a switch.
 *
 * <p>Folders are always grouped before files regardless of the user-selected order — a hard MT
 * Manager parity expectation. The wrapper inverts {@code isFolder()} (true sorts last in
 * natural order, so {@code reversed()} puts directories first), then chains the per-strategy
 * comparator.
 *
 * <p>Phase R-5b migrated from {@link FileNode} to {@link VirtualNode} — same sort semantics,
 * different node type.
 */
public enum SortOrder {
    NAME_ASC(Comparator.comparing(n -> safe(n.name()))),
    NAME_DESC(Comparator.comparing((VirtualNode n) -> safe(n.name())).reversed()),
    SIZE_DESC(Comparator.comparingLong(VirtualNode::size).reversed()),
    SIZE_ASC(Comparator.comparingLong(VirtualNode::size)),
    DATE_DESC(Comparator.comparingLong(VirtualNode::modifiedAt).reversed()),
    DATE_ASC(Comparator.comparingLong(VirtualNode::modifiedAt)),
    TYPE(Comparator
            .comparing((VirtualNode n) -> extension(n.name()))
            .thenComparing(n -> safe(n.name())));

    public static final SortOrder DEFAULT = NAME_ASC;

    private final Comparator<VirtualNode> base;

    SortOrder(Comparator<VirtualNode> base) {
        this.base = base;
    }

    /**
     * The full comparator the browser should apply: directories first, then this strategy.
     */
    @NonNull
    public Comparator<VirtualNode> folderFirstComparator() {
        return Comparator.comparing(VirtualNode::isFolder).reversed().thenComparing(base);
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

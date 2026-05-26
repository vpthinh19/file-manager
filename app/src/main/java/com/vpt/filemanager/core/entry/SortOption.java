package com.vpt.filemanager.core.entry;

import java.util.Comparator;

public enum SortOption {
    NAME_ASC(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)),
    NAME_DESC(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).reversed()),
    SIZE_DESC(Comparator.comparingLong(Entry::size).reversed()),
    SIZE_ASC(Comparator.comparingLong(Entry::size)),
    DATE_DESC(Comparator.comparingLong(Entry::modifiedAt).reversed()),
    DATE_ASC(Comparator.comparingLong(Entry::modifiedAt)),
    TYPE(Comparator.comparing((Entry entry) -> extension(entry.name()),
            String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));

    public static final SortOption DEFAULT = NAME_ASC;
    private final Comparator<Entry> order;

    SortOption(Comparator<Entry> order) {
        this.order = order;
    }

    public Comparator<Entry> comparator() {
        return Comparator.comparing(Entry::isParent).reversed()
                .thenComparing(Comparator.comparing(Entry::isFolder).reversed())
                .thenComparing(order);
    }

    public static SortOption safeValueOf(String raw) {
        if (raw == null) return DEFAULT;
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}

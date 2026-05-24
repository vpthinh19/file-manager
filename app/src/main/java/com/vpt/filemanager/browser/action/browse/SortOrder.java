package com.vpt.filemanager.browser.action.browse;

import androidx.annotation.NonNull;

import java.util.Comparator;

import com.vpt.filemanager.browser.item.Item;

public enum SortOrder {
    NAME_ASC(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER)),
    NAME_DESC(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER).reversed()),
    SIZE_DESC(Comparator.comparingLong(Item::size).reversed()),
    SIZE_ASC(Comparator.comparingLong(Item::size)),
    DATE_DESC(Comparator.comparingLong(Item::modifiedAt).reversed()),
    DATE_ASC(Comparator.comparingLong(Item::modifiedAt)),
    TYPE(Comparator.comparing((Item item) -> extension(item.name()),
            String.CASE_INSENSITIVE_ORDER).thenComparing(Item::name,
            String.CASE_INSENSITIVE_ORDER));

    public static final SortOrder DEFAULT = NAME_ASC;
    private final Comparator<Item> comparator;

    SortOrder(Comparator<Item> comparator) {
        this.comparator = comparator;
    }

    public Comparator<Item> comparator() {
        return Comparator.comparing(Item::isParent).reversed()
                .thenComparing(Comparator.comparing(Item::isFolder).reversed())
                .thenComparing(comparator);
    }

    @NonNull
    public static SortOrder safeValueOf(String raw) {
        if (raw == null) return DEFAULT;
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }

    private static String extension(String value) {
        int dot = value.lastIndexOf('.');
        return dot < 0 ? "" : value.substring(dot + 1);
    }
}

package com.vpt.filemanager.support;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Tiny helper: when "Keep both" is chosen for a name conflict, derive a unique name by appending
 * " (1)", " (2)", … to the base portion (preserving the extension) until {@link File#exists()}
 * returns false. KISS, used by both create and (later) copy/move flows.
 */
public final class NameDeconflict {
    private NameDeconflict() {
    }

    private static final int MAX_ATTEMPTS = 1000;

    /**
     * @return the original {@code name} if no file with that name exists in {@code dir}; otherwise
     * the name with " (N)" inserted before the extension for the smallest N starting at 1 such that
     * no file collides. Falls back to appending a timestamp if 1000 attempts didn't find a slot
     * (effectively unreachable but keeps the contract: never returns a colliding name).
     */
    @NonNull
    public static String unique(@NonNull File dir, @NonNull String name) {
        if (!new File(dir, name).exists()) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot <= 0 ? name : name.substring(0, dot);
        String ext = dot <= 0 ? "" : name.substring(dot);
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!new File(dir, candidate).exists()) {
                return candidate;
            }
        }
        return base + "_" + System.currentTimeMillis() + ext;
    }
}

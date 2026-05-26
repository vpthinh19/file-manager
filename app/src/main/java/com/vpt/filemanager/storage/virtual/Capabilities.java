package com.vpt.filemanager.storage.virtual;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.path.Path;

import java.util.EnumSet;

/** Derives the actions available at a location from its storage's write ability. */
public final class Capabilities {
    private Capabilities() {
    }

    @NonNull
    public static EnumSet<Capability> of(@NonNull Storage storage, @NonNull Path path) {
        EnumSet<Capability> result = EnumSet.of(Capability.COPY_OUT, Capability.OPEN_WITH,
                Capability.SHARE);
        if (storage.canWrite(path)) {
            result.addAll(EnumSet.of(Capability.CREATE, Capability.RENAME, Capability.DELETE,
                    Capability.MOVE_IN, Capability.MOVE_OUT, Capability.EDIT_CONTENT));
        }
        return result;
    }
}

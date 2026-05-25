package com.vpt.filemanager.handler;

import com.vpt.filemanager.model.ContentKind;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.resolver.ResolveResult;

import java.io.File;

import javax.inject.Inject;

public final class MediaHandler {
    @Inject public MediaHandler() {}
    public ResolveResult.Content open(Location source, File file, boolean video, Location archiveEntry) {
        return new ResolveResult.Content(source, file.getAbsolutePath(), file.getName(),
                video ? ContentKind.VIDEO : ContentKind.AUDIO, true, archiveEntry);
    }
}

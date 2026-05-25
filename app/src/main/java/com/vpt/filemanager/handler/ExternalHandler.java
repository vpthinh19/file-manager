package com.vpt.filemanager.handler;

import com.vpt.filemanager.model.ContentKind;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.resolver.ResolveResult;

import java.io.File;

import javax.inject.Inject;

public final class ExternalHandler {
    @Inject public ExternalHandler() {}
    public ResolveResult.Content open(Location source, File file, Location archiveEntry) {
        return new ResolveResult.Content(source, file.getAbsolutePath(), file.getName(),
                ContentKind.EXTERNAL, true, archiveEntry);
    }
}

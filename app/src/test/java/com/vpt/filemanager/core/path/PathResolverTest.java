package com.vpt.filemanager.core.path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.vpt.filemanager.core.detect.ContentDetector;
import com.vpt.filemanager.core.detect.ContentType;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.handler.HandlerRegistry;
import com.vpt.filemanager.handler.HandlerResult;
import com.vpt.filemanager.handler.OtherHandler;
import com.vpt.filemanager.handler.TextHandler;
import com.vpt.filemanager.storage.FakeStorage;
import com.vpt.filemanager.storage.StorageRegistry;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

public final class PathResolverTest {
    private static PathResolver resolverWith(FakeStorage storage) {
        return new PathResolver(
                new StorageRegistry(Set.of(storage)),
                new HandlerRegistry(Set.of(new TextHandler()), new OtherHandler()),
                new ContentDetector());
    }

    @Test
    public void containerPathReturnsItsListing() throws Exception {
        FakeStorage storage = new FakeStorage(p -> true);
        storage.container = true;
        storage.entries = List.of(
                Entry.local(Path.storage("/dir/x.txt"), "/x.txt", "x.txt", false, 1, 0));

        HandlerResult result = resolverWith(storage).open(Path.storage("/dir"));

        assertTrue(result instanceof HandlerResult.Entries);
        assertEquals(1, ((HandlerResult.Entries) result).entries().size());
    }

    @Test
    public void textFileIsDispatchedToTheTextHandler() throws Exception {
        File file = File.createTempFile("resolver-text", ".dat");
        file.deleteOnExit();
        Files.write(file.toPath(), "plain readable text\nsecond line\n".getBytes(StandardCharsets.UTF_8));

        FakeStorage storage = new FakeStorage(p -> true);
        storage.container = false;
        storage.materialized = file;

        HandlerResult result = resolverWith(storage).open(Path.storage("/a.dat"));

        assertTrue(result instanceof HandlerResult.OpenContent);
        assertEquals(ContentType.TEXT, ((HandlerResult.OpenContent) result).type());
    }

    @Test
    public void binaryFileFallsBackToLaunchIntent() throws Exception {
        File file = File.createTempFile("resolver-bin", ".dat");
        file.deleteOnExit();
        // A null byte means "not text", and no image/audio/video magic matches -> OTHER.
        Files.write(file.toPath(), new byte[] {0x00, 0x01, 0x02, 0x00, 0x7f});

        FakeStorage storage = new FakeStorage(p -> true);
        storage.container = false;
        storage.materialized = file;

        HandlerResult result = resolverWith(storage).open(Path.storage("/blob.dat"));

        assertTrue(result instanceof HandlerResult.LaunchIntent);
    }
}

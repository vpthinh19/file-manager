package com.vpt.filemanager.core.format;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ExtensionRegistryTest {
    private final ExtensionRegistry extensions = new ExtensionRegistry();

    @Test
    public void routesSupportedFilesOnlyByName() {
        assertEquals(ExtensionRegistry.Type.ARCHIVE, extensions.classify("bundle.TAR.GZ"));
        assertEquals(ExtensionRegistry.Type.APK_INSTALLER, extensions.classify("client.apk"));
        assertEquals(ExtensionRegistry.Type.EXTERNAL, extensions.classify("book.epub"));
        assertEquals(ExtensionRegistry.Type.TEXT, extensions.classify("vector.svg"));
        assertEquals(ExtensionRegistry.Type.TEXT, extensions.classify(".gitignore"));
    }

    @Test
    public void undecoratedNamesAskAndUnknownExtensionsOpenExternally() {
        assertEquals(ExtensionRegistry.Type.OPEN_AS, extensions.classify("README"));
        assertEquals(ExtensionRegistry.Type.OPEN_AS, extensions.classify("LICENSE"));
        assertEquals(ExtensionRegistry.Type.EXTERNAL, extensions.classify("payload.unknown"));
    }

    @Test
    public void extensionDoesNotInspectOrFallbackForContents() {
        assertEquals(ExtensionRegistry.Type.TEXT, extensions.classify("binary-as-text.txt"));
        assertEquals(ExtensionRegistry.Type.ARCHIVE, extensions.classify("broken.zip"));
    }
}

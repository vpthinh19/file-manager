package com.vpt.filemanager.core.format;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ExtensionRegistryTest {
    private final ExtensionRegistry extensions = new ExtensionRegistry();

    @Test
    public void routesSupportedFilesOnlyByName() {
        assertEquals(ExtensionRegistry.Kind.ARCHIVE, extensions.classify("bundle.TAR.GZ"));
        assertEquals(ExtensionRegistry.Kind.APK_INSTALLER, extensions.classify("client.apk"));
        assertEquals(ExtensionRegistry.Kind.EXTERNAL, extensions.classify("book.epub"));
        assertEquals(ExtensionRegistry.Kind.TEXT, extensions.classify("vector.svg"));
        assertEquals(ExtensionRegistry.Kind.TEXT, extensions.classify(".gitignore"));
    }

    @Test
    public void undecoratedNamesAskAndUnknownExtensionsOpenExternally() {
        assertEquals(ExtensionRegistry.Kind.OPEN_AS, extensions.classify("README"));
        assertEquals(ExtensionRegistry.Kind.OPEN_AS, extensions.classify("LICENSE"));
        assertEquals(ExtensionRegistry.Kind.EXTERNAL, extensions.classify("payload.unknown"));
    }

    @Test
    public void extensionDoesNotInspectOrFallbackForContents() {
        assertEquals(ExtensionRegistry.Kind.TEXT, extensions.classify("binary-as-text.txt"));
        assertEquals(ExtensionRegistry.Kind.ARCHIVE, extensions.classify("broken.zip"));
    }
}

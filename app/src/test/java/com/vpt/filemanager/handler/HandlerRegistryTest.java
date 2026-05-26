package com.vpt.filemanager.handler;

import static org.junit.Assert.assertSame;

import com.vpt.filemanager.core.detect.ContentType;

import org.junit.Test;

import java.util.Set;

public final class HandlerRegistryTest {
    @Test
    public void indexesHandlersByDeclaredType() {
        TextHandler text = new TextHandler();
        ImageHandler image = new ImageHandler();
        OtherHandler fallback = new OtherHandler();
        HandlerRegistry registry = new HandlerRegistry(Set.of(text, image), fallback);

        assertSame(text, registry.handlerFor(ContentType.TEXT));
        assertSame(image, registry.handlerFor(ContentType.IMAGE));
    }

    @Test
    public void fallsBackToOtherHandlerForUnregisteredTypes() {
        OtherHandler fallback = new OtherHandler();
        HandlerRegistry registry = new HandlerRegistry(Set.of(new TextHandler()), fallback);

        // AUDIO has no registered handler in this set -> the fallback answers.
        assertSame(fallback, registry.handlerFor(ContentType.AUDIO));
    }
}

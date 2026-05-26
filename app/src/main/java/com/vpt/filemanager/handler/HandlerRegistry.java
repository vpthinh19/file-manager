package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.format.ExtensionRegistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Maps an {@link ExtensionRegistry.Type} to the {@link Handler} that opens it.
 *
 * <p>Hilt collects every {@code @IntoSet Handler} binding; this registry indexes them by their
 * declared {@link Handler#type()} once at construction. Types with no dedicated handler
 * (e.g. {@code APK_INSTALLER}) fall back to {@link OtherHandler}.
 */
@Singleton
public final class HandlerRegistry {
    private final Map<ExtensionRegistry.Type, Handler> byType = new EnumMap<>(ExtensionRegistry.Type.class);
    private final Handler fallback;

    @Inject
    public HandlerRegistry(Set<Handler> handlers, OtherHandler fallback) {
        this.fallback = fallback;
        for (Handler handler : handlers) byType.put(handler.type(), handler);
    }

    @NonNull
    public Handler handlerFor(@NonNull ExtensionRegistry.Type type) {
        Handler handler = byType.get(type);
        return handler != null ? handler : fallback;
    }
}

package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.core.format.ExtensionRegistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Indexes the injected {@link Handler} set by {@link Handler#type()}. Types without a dedicated
 * handler (e.g. {@code APK_INSTALLER}) fall back to {@link OtherHandler}.
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

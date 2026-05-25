package com.vpt.filemanager.handler;

import androidx.annotation.NonNull;

import com.vpt.filemanager.content.ContentType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Maps a {@link ContentType} to the {@link Handler} that renders it.
 *
 * <p>Hilt collects every {@code @IntoSet Handler} binding; this registry
 * indexes them by their declared {@link Handler#type()} once at construction.
 */
@Singleton
public final class HandlerRegistry {
    private final Map<ContentType, Handler> byType = new EnumMap<>(ContentType.class);
    private final Handler fallback;

    @Inject
    public HandlerRegistry(Set<Handler> handlers, OtherHandler fallback) {
        this.fallback = fallback;
        for (Handler handler : handlers) byType.put(handler.type(), handler);
    }

    @NonNull
    public Handler handlerFor(@NonNull ContentType type) {
        Handler handler = byType.get(type);
        return handler != null ? handler : fallback;
    }
}

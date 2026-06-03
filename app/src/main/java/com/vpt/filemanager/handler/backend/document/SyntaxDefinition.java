package com.vpt.filemanager.handler.backend.document;

import androidx.annotation.Nullable;

import java.util.List;

/** Immutable grammar registration: asset paths only, no grammar objects (built lazily by {@link SyntaxSetup}). */
final class SyntaxDefinition {
    final String name;
    final String displayName;
    final String scopeName;
    final String grammarAsset;
    @Nullable
    final String configurationAsset;
    final List<String> dependencies;

    SyntaxDefinition(
            String name,
            String displayName,
            String scopeName,
            String grammarAsset,
            @Nullable String configurationAsset,
            List<String> dependencies) {
        this.name = name;
        this.displayName = displayName;
        this.scopeName = scopeName;
        this.grammarAsset = grammarAsset;
        this.configurationAsset = configurationAsset;
        this.dependencies = dependencies;
    }
}

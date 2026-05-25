package com.vpt.filemanager.ui.content.editor;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Immutable TextMate grammar registration record.
 *
 * <p>The record contains asset paths only. Grammar objects are created lazily by
 * {@link SyntaxSetup} for the scopes actually requested by an editor instance.
 */
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

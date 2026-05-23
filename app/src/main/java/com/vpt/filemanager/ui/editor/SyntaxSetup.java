package com.vpt.filemanager.ui.editor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;

import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IThemeSource;

/**
 * Process-wide bridge between app assets and sora-editor's TextMate registries.
 *
 * <p>Theme/provider state is warmed once and shared. Grammars are deliberately lazy: loading all
 * available TM4E grammars on the first editor open would front-load parser work and retain grammar
 * data for languages the user never opens.
 */
public final class SyntaxSetup {
    private static boolean providerRegistered;
    @Nullable
    private static String activeThemeName;

    private SyntaxSetup() {
    }

    /**
     * Cheap process warm-up used by the application background executor.
     */
    public static synchronized void prewarm(@NonNull Context context) throws IOException {
        ensureInfrastructure(context);
    }

    /**
     * Build a language instance for one document, loading its grammar on demand.
     *
     * @return a language instance or {@code null} when no catalog entry exists for the scope.
     */
    @Nullable
    public static synchronized TextMateLanguage createLanguage(
            @NonNull Context context,
            @Nullable String scopeName) throws IOException {
        SyntaxDefinition definition = SyntaxCatalog.find(scopeName);
        if (definition == null) {
            return null;
        }
        ensureInfrastructure(context);
        loadDefinition(definition, new HashSet<>());
        return TextMateLanguage.create(definition.scopeName, true);
    }

    /**
     * Kept for compatibility with instrumentation tests and explicit callers. Unlike the old
     * implementation, this does not load every grammar.
     */
    public static synchronized void ensureInitialized(@NonNull Context context) throws IOException {
        ensureInfrastructure(context);
    }

    public static synchronized void applyTheme(@NonNull Context context) throws IOException {
        ensureProvider(context);
        applyThemeIfRequired(context);
    }

    private static void ensureInfrastructure(Context context) throws IOException {
        ensureProvider(context);
        applyThemeIfRequired(context);
    }

    private static void ensureProvider(Context context) {
        if (providerRegistered) {
            return;
        }
        FileProviderRegistry.getInstance().addFileProvider(
                new AssetsFileResolver(context.getApplicationContext().getAssets()));
        providerRegistered = true;
    }

    private static void applyThemeIfRequired(Context context) throws IOException {
        String themeName = SyntaxThemeProvider.themeName(context);
        if (themeName.equals(activeThemeName)
                && ThemeRegistry.getInstance().getCurrentThemeModel() != null) {
            return;
        }
        String assetPath = SyntaxThemeProvider.assetPath(context);
        try (InputStream input = FileProviderRegistry.getInstance().tryGetInputStream(assetPath)) {
            if (input == null) {
                throw new IOException("Theme asset missing: " + assetPath);
            }
            ThemeModel model = new ThemeModel(
                    IThemeSource.fromInputStream(input, assetPath, null),
                    themeName);
            ThemeRegistry.getInstance().loadTheme(model);
            ThemeRegistry.getInstance().setTheme(themeName);
            activeThemeName = themeName;
        } catch (Exception error) {
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Theme load failed: " + assetPath, error);
        }
    }

    private static void loadDefinition(SyntaxDefinition definition, Set<String> visiting)
            throws IOException {
        GrammarRegistry registry = GrammarRegistry.getInstance();
        if (registry.findGrammar(definition.scopeName, false) != null) {
            return;
        }
        if (!visiting.add(definition.scopeName)) {
            throw new IOException("Cyclic grammar dependency: " + definition.scopeName);
        }
        for (String dependencyScope : definition.dependencies) {
            SyntaxDefinition dependency = SyntaxCatalog.find(dependencyScope);
            if (dependency != null) {
                loadDefinition(dependency, visiting);
            }
        }
        try (InputStream input =
                     FileProviderRegistry.getInstance().tryGetInputStream(definition.grammarAsset)) {
            if (input == null) {
                throw new IOException("Grammar asset missing: " + definition.grammarAsset);
            }
            IGrammarSource grammarSource =
                    IGrammarSource.fromInputStream(input, definition.grammarAsset, null);
            if (definition.configurationAsset == null) {
                registry.loadGrammar(DefaultGrammarDefinition.withGrammarSource(
                        grammarSource, definition.name, definition.scopeName));
            } else {
                registry.loadGrammar(DefaultGrammarDefinition.withLanguageConfiguration(
                        grammarSource,
                        definition.configurationAsset,
                        definition.name,
                        definition.scopeName));
            }
        } catch (RuntimeException error) {
            throw new IOException("Grammar load failed: " + definition.grammarAsset, error);
        } finally {
            visiting.remove(definition.scopeName);
        }
    }
}

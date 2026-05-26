package com.vpt.filemanager.handler.backend.document;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private static final int MAX_CACHED_GRAMMAR_SCOPES = 3;
    private static final Map<String, CachedGrammar> GRAMMAR_CACHE =
            new LinkedHashMap<>(MAX_CACHED_GRAMMAR_SCOPES + 1, 0.75f, true);
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
    public static synchronized LanguageLease acquireLanguage(
            @NonNull Context context,
            @Nullable String scopeName) throws IOException {
        SyntaxDefinition definition = SyntaxCatalog.find(scopeName);
        if (definition == null) {
            return null;
        }
        ensureInfrastructure(context);
        CachedGrammar grammar = GRAMMAR_CACHE.get(definition.scopeName);
        if (grammar == null) {
            GrammarRegistry registry = new GrammarRegistry(null);
            loadDefinition(definition, new HashSet<>(), registry);
            applyTheme(registry);
            grammar = new CachedGrammar(registry);
            GRAMMAR_CACHE.put(definition.scopeName, grammar);
        } else {
            applyTheme(grammar.registry);
        }
        grammar.activeLeases++;
        try {
            TextMateLanguage language = TextMateLanguage.create(
                    definition.scopeName,
                    grammar.registry,
                    ThemeRegistry.getInstance(),
                    true);
            trimGrammarCache();
            return new LanguageLease(language, grammar);
        } catch (RuntimeException failure) {
            grammar.activeLeases--;
            trimGrammarCache();
            throw failure;
        }
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

    private static void loadDefinition(
            SyntaxDefinition definition,
            Set<String> visiting,
            GrammarRegistry registry)
            throws IOException {
        if (registry.findGrammar(definition.scopeName, false) != null) {
            return;
        }
        if (!visiting.add(definition.scopeName)) {
            throw new IOException("Cyclic grammar dependency: " + definition.scopeName);
        }
        for (String dependencyScope : definition.dependencies) {
            SyntaxDefinition dependency = SyntaxCatalog.find(dependencyScope);
            if (dependency != null) {
                loadDefinition(dependency, visiting, registry);
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

    private static void applyTheme(GrammarRegistry registry) throws IOException {
        try {
            registry.setTheme(ThemeRegistry.getInstance().getCurrentThemeModel());
        } catch (Exception error) {
            throw new IOException("Unable to apply TextMate theme", error);
        }
    }

    private static void trimGrammarCache() {
        if (GRAMMAR_CACHE.size() <= MAX_CACHED_GRAMMAR_SCOPES) {
            return;
        }
        Iterator<Map.Entry<String, CachedGrammar>> entries = GRAMMAR_CACHE.entrySet().iterator();
        while (GRAMMAR_CACHE.size() > MAX_CACHED_GRAMMAR_SCOPES && entries.hasNext()) {
            CachedGrammar grammar = entries.next().getValue();
            if (grammar.activeLeases == 0) {
                entries.remove();
                grammar.registry.dispose();
            }
        }
    }

    private static void release(CachedGrammar grammar) {
        if (grammar.activeLeases > 0) {
            grammar.activeLeases--;
        }
        trimGrammarCache();
    }

    private static final class CachedGrammar {
        final GrammarRegistry registry;
        int activeLeases;

        CachedGrammar(GrammarRegistry registry) {
            this.registry = registry;
        }
    }

    /**
     * Keeps one grammar registry alive only while an editor uses it or it remains in the small MRU.
     */
    public static final class LanguageLease implements AutoCloseable {
        private final TextMateLanguage language;
        private CachedGrammar grammar;

        private LanguageLease(TextMateLanguage language, CachedGrammar grammar) {
            this.language = language;
            this.grammar = grammar;
        }

        @NonNull
        public TextMateLanguage language() {
            return language;
        }

        @Override
        public void close() {
            synchronized (SyntaxSetup.class) {
                if (grammar == null) {
                    return;
                }
                release(grammar);
                grammar = null;
            }
        }
    }
}

package com.vpt.filemanager.ui.editor;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import org.eclipse.tm4e.core.registry.IThemeSource;

/**
 * One-time process-wide setup cho TextMate engine: register asset file provider, load grammar
 * index, register active theme. Phase R-9.
 *
 * <p>Pattern: idempotent {@link #ensureInitialized(Context)} — lần đầu chạy load + activate, các
 * lần sau là no-op (guard bằng flag boolean). Theme có thể đổi runtime khi user toggle night mode
 * → gọi lại {@link #applyTheme(Context)} để re-activate (cần thiết khi config change).
 *
 * <p>Không inject qua Hilt — registry là static singleton trong sora-editor library, không cần
 * lifecycle scope. Class này wraps cho discoverability + thread safety.
 */
public final class SyntaxSetup {
    private static final String LANGUAGES_INDEX = "editor/textmate/languages.json";

    private static boolean initialized = false;

    private SyntaxSetup() {
    }

    /**
     * Lazy init grammars + theme. Safe to call multiple times — initialization chỉ chạy 1 lần
     * process. Throw {@link IOException} nếu asset bundle bị thiếu (build bug, không recover).
     */
    public static synchronized void ensureInitialized(@NonNull Context ctx) throws IOException {
        if (initialized) {
            applyTheme(ctx);
            return;
        }
        FileProviderRegistry.getInstance().addFileProvider(
                new AssetsFileResolver(ctx.getApplicationContext().getAssets()));
        GrammarRegistry.getInstance().loadGrammars(LANGUAGES_INDEX);
        applyTheme(ctx);
        initialized = true;
    }

    /**
     * Re-apply theme theo night mode hiện tại. Tách khỏi init để config-change (system toggle
     * dark) có thể swap theme mà không reload grammars (chi phí cao).
     */
    public static synchronized void applyTheme(@NonNull Context ctx) throws IOException {
        String themeName = SyntaxThemeProvider.themeName(ctx);
        String assetPath = SyntaxThemeProvider.assetPath(ctx);
        try (InputStream in = FileProviderRegistry.getInstance().tryGetInputStream(assetPath)) {
            if (in == null) {
                throw new IOException("Theme asset missing: " + assetPath);
            }
            ThemeModel model = new ThemeModel(
                    IThemeSource.fromInputStream(in, assetPath, null),
                    themeName);
            ThemeRegistry.getInstance().loadTheme(model);
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Theme load failed: " + assetPath, e);
        }
        ThemeRegistry.getInstance().setTheme(themeName);
    }
}

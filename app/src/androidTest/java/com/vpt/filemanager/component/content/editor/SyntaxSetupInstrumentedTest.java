package com.vpt.filemanager.component.content.editor;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.res.Configuration;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;

@RunWith(AndroidJUnit4.class)
public final class SyntaxSetupInstrumentedTest {

    @Test
    public void textMateRegistryLoadsBundledGrammarsAndCurrentTheme() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        SyntaxSetup.ensureInitialized(context);
        try (SyntaxSetup.LanguageLease javaLease =
                     SyntaxSetup.acquireLanguage(context, "source.java");
             SyntaxSetup.LanguageLease jsonLease =
                     SyntaxSetup.acquireLanguage(context, "source.json");
             SyntaxSetup.LanguageLease xmlLease =
                     SyntaxSetup.acquireLanguage(context, "text.xml")) {
            TextMateLanguage javaLanguage = javaLease.language();
            TextMateLanguage jsonLanguage = jsonLease.language();
            TextMateLanguage xmlLanguage = xmlLease.language();

            assertNotNull(javaLanguage);
            assertNotNull(jsonLanguage);
            assertNotNull(xmlLanguage);
            assertNotNull(TextMateColorScheme.create(ThemeRegistry.getInstance()));

            javaLanguage.destroy();
            jsonLanguage.destroy();
            xmlLanguage.destroy();
        }
    }

    @Test
    public void textMateThemesLoadInLightAndDarkMode() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        SyntaxSetup.ensureInitialized(withNightMode(context, Configuration.UI_MODE_NIGHT_NO));
        assertNotNull(ThemeRegistry.getInstance().getCurrentThemeModel());

        SyntaxSetup.applyTheme(withNightMode(context, Configuration.UI_MODE_NIGHT_YES));
        assertNotNull(ThemeRegistry.getInstance().getCurrentThemeModel());
        assertNotNull(TextMateColorScheme.create(ThemeRegistry.getInstance()));
    }

    private static Context withNightMode(Context context, int nightMode) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return context.createConfigurationContext(config);
    }
}

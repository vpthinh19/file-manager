package com.vpt.filemanager.ui.editor;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SyntaxAssetContractTest {
    private static final Path ASSET_ROOT = Path.of("src/main/assets");
    private static final Path TEXTMATE_ROOT = ASSET_ROOT.resolve("editor/textmate");
    private static final Pattern GRAMMAR_PATH =
            Pattern.compile("\"grammar\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern INCLUDE_PATH =
            Pattern.compile("\"include\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    public void languageIndexUsesSoraExpectedWrapperFormat() throws IOException {
        String json = read(TEXTMATE_ROOT.resolve("languages.json")).trim();

        assertTrue("sora-editor expects languages.json to be a JSON object", json.startsWith("{"));
        assertTrue("sora-editor expects languages.json to contain languages",
                json.contains("\"languages\""));
        assertTrue("sora-editor expects languages to be a JSON array",
                Pattern.compile("\"languages\"\\s*:\\s*\\[").matcher(json).find());
    }

    @Test
    public void languageIndexReferencesExistingGrammarAssets() throws IOException {
        Matcher matcher = GRAMMAR_PATH.matcher(read(TEXTMATE_ROOT.resolve("languages.json")));

        while (matcher.find()) {
            String grammarPath = matcher.group(1);
            assertTrue(
                    "Missing grammar asset referenced by languages.json: " + grammarPath,
                    Files.isRegularFile(ASSET_ROOT.resolve(grammarPath)));
        }
    }

    @Test
    public void themeIncludesReferenceExistingThemeAssets() throws IOException {
        Path themes = TEXTMATE_ROOT.resolve("themes");
        try (var stream = Files.list(themes)) {
            for (Path theme : stream.filter(path -> path.toString().endsWith(".json")).toList()) {
                Matcher matcher = INCLUDE_PATH.matcher(read(theme));
                if (!matcher.find()) {
                    continue;
                }
                String includePath = matcher.group(1);
                Path resolved = theme.getParent().resolve(includePath).normalize();

                assertTrue(
                        "Missing theme include referenced by " + theme.getFileName() + ": " + includePath,
                        Files.isRegularFile(resolved));
            }
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

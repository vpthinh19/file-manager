package com.vpt.filemanager.texteditor.ui;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SyntaxAssetContractTest {
    private static final Path ASSET_ROOT = Path.of("src/main/assets");

    @Test
    public void catalogReferencesExistingGrammarAndConfigurationAssets() {
        for (SyntaxDefinition definition : SyntaxCatalog.all()) {
            assertTrue(
                    "Missing grammar asset for " + definition.scopeName + ": "
                            + definition.grammarAsset,
                    Files.isRegularFile(ASSET_ROOT.resolve(definition.grammarAsset)));
            if (definition.configurationAsset != null) {
                assertTrue(
                        "Missing language configuration for " + definition.scopeName + ": "
                                + definition.configurationAsset,
                        Files.isRegularFile(ASSET_ROOT.resolve(definition.configurationAsset)));
            }
        }
    }

    @Test
    public void dependencyScopesExistInCatalog() {
        for (SyntaxDefinition definition : SyntaxCatalog.all()) {
            for (String dependency : definition.dependencies) {
                assertTrue(
                        "Unknown dependency " + dependency + " for " + definition.scopeName,
                        SyntaxCatalog.find(dependency) != null);
            }
        }
    }

    @Test
    public void expandedTm4eCatalogContainsBroadLanguageCoverage() {
        assertTrue(SyntaxCatalog.all().size() >= 50);
        assertTrue(SyntaxCatalog.find("source.java") != null);
        assertTrue(SyntaxCatalog.find("source.powershell") != null);
        assertTrue(SyntaxCatalog.find("source.swift") != null);
        assertTrue(SyntaxCatalog.find("text.html.cshtml") != null);
    }
}

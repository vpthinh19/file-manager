package com.vpt.filemanager.ui.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Phase R-9: unit test cho {@link LanguageResolver}. Pure JVM — không cần Robolectric.
 *
 * <p>Coverage: extension mapping (positive + case-insensitive + edge), Dockerfile special case,
 * unknown fallback.
 */
public final class LanguageResolverTest {

    @Test
    public void java_extension_maps_to_source_java() {
        assertEquals("source.java", LanguageResolver.scopeFor("Main.java"));
    }

    @Test
    public void uppercase_extension_resolves_same() {
        assertEquals("source.java", LanguageResolver.scopeFor("MAIN.JAVA"));
        assertEquals("source.python", LanguageResolver.scopeFor("Script.PY"));
    }

    @Test
    public void kotlin_kts_maps_to_gradle_kotlin_dsl_scope() {
        // VS Code Gradle Kotlin DSL extension EXTENDS Kotlin syntax — đúng scope cho v1.
        assertEquals("source.gradle-kotlin-dsl", LanguageResolver.scopeFor("App.kt"));
        assertEquals("source.gradle-kotlin-dsl", LanguageResolver.scopeFor("build.gradle.kts"));
    }

    @Test
    public void javascript_variants() {
        assertEquals("source.js", LanguageResolver.scopeFor("index.js"));
        assertEquals("source.js", LanguageResolver.scopeFor("module.mjs"));
        assertEquals("source.js", LanguageResolver.scopeFor("legacy.cjs"));
        assertEquals("source.js", LanguageResolver.scopeFor("Component.jsx"));
    }

    @Test
    public void typescript_variants() {
        assertEquals("source.ts", LanguageResolver.scopeFor("types.ts"));
        assertEquals("source.ts", LanguageResolver.scopeFor("App.tsx"));
    }

    @Test
    public void html_xml_markdown_variants() {
        assertEquals("text.html.basic", LanguageResolver.scopeFor("page.html"));
        assertEquals("text.html.basic", LanguageResolver.scopeFor("page.htm"));
        assertEquals("text.xml", LanguageResolver.scopeFor("AndroidManifest.xml"));
        assertEquals("text.xml", LanguageResolver.scopeFor("schema.xsd"));
        assertEquals("text.html.markdown", LanguageResolver.scopeFor("README.md"));
        assertEquals("text.html.markdown", LanguageResolver.scopeFor("notes.markdown"));
    }

    @Test
    public void cpp_variants_all_resolve() {
        assertEquals("source.cpp", LanguageResolver.scopeFor("main.cpp"));
        assertEquals("source.cpp", LanguageResolver.scopeFor("util.hpp"));
        assertEquals("source.cpp", LanguageResolver.scopeFor("Header.hh"));
        assertEquals("source.cpp", LanguageResolver.scopeFor("a.cxx"));
        assertEquals("source.c", LanguageResolver.scopeFor("stdio.h"));
        assertEquals("source.c", LanguageResolver.scopeFor("hello.c"));
    }

    @Test
    public void shell_variants() {
        assertEquals("source.shell", LanguageResolver.scopeFor("install.sh"));
        assertEquals("source.shell", LanguageResolver.scopeFor("setup.bash"));
        assertEquals("source.shell", LanguageResolver.scopeFor("rc.zsh"));
    }

    @Test
    public void yaml_variants() {
        assertEquals("source.yaml", LanguageResolver.scopeFor("config.yml"));
        assertEquals("source.yaml", LanguageResolver.scopeFor("config.yaml"));
    }

    @Test
    public void json_variants() {
        assertEquals("source.json", LanguageResolver.scopeFor("package.json"));
        assertEquals("source.json", LanguageResolver.scopeFor("tsconfig.jsonc"));
    }

    @Test
    public void rust_go_ruby_php_lua_sql() {
        assertEquals("source.rust", LanguageResolver.scopeFor("main.rs"));
        assertEquals("source.go", LanguageResolver.scopeFor("server.go"));
        assertEquals("source.ruby", LanguageResolver.scopeFor("Rakefile.rb"));
        assertEquals("source.php", LanguageResolver.scopeFor("index.php"));
        assertEquals("source.lua", LanguageResolver.scopeFor("init.lua"));
        assertEquals("source.sql", LanguageResolver.scopeFor("schema.sql"));
    }

    @Test
    public void dockerfile_no_extension_special_case() {
        assertEquals("source.dockerfile", LanguageResolver.scopeFor("Dockerfile"));
        assertEquals("source.dockerfile", LanguageResolver.scopeFor("DOCKERFILE"));
        assertEquals("source.dockerfile", LanguageResolver.scopeFor("build.dockerfile"));
    }

    @Test
    public void unknown_extension_returns_null() {
        assertNull(LanguageResolver.scopeFor("data.bin"));
        assertNull(LanguageResolver.scopeFor("photo.jpg"));
        assertNull(LanguageResolver.scopeFor("song.mp3"));
        assertNull(LanguageResolver.scopeFor("archive.zip"));
    }

    @Test
    public void no_extension_returns_null() {
        assertNull(LanguageResolver.scopeFor("README"));
        assertNull(LanguageResolver.scopeFor("LICENSE"));
    }

    @Test
    public void trailing_dot_returns_null() {
        assertNull(LanguageResolver.scopeFor("weird."));
    }

    @Test
    public void hidden_files_with_known_ext_resolve() {
        // .bashrc → ext=bashrc not in map → null. .gitignore similar.
        // But .config.yml → ext=yml → resolve.
        assertEquals("source.yaml", LanguageResolver.scopeFor(".config.yml"));
        assertNull(LanguageResolver.scopeFor(".bashrc"));
    }

    @Test
    public void empty_filename_returns_null() {
        assertNull(LanguageResolver.scopeFor(""));
    }

    @Test
    public void dot_only_filename_returns_null() {
        // "." có dot ở index 0 → ext rỗng → null
        assertNull(LanguageResolver.scopeFor("."));
    }

    @Test
    public void multi_dot_filename_uses_last_extension() {
        // "a.b.java" → lastIndexOf('.') match phần đuôi → ext=java → source.java
        assertEquals("source.java", LanguageResolver.scopeFor("a.b.java"));
        assertEquals("source.json", LanguageResolver.scopeFor("package-lock.json"));
        assertEquals("source.gradle-kotlin-dsl", LanguageResolver.scopeFor("build.gradle.kts"));
    }
}

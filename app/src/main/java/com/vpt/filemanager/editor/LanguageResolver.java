package com.vpt.filemanager.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Map từ filename → TextMate scope name của grammar tương ứng. Phase R-9.
 *
 * <p>Scope name là contract giữa grammar registry và editor: e.g. file {@code Main.java} sẽ resolve
 * sang {@code source.java}, từ đó sora-editor's {@link
 * io.github.rosemoe.sora.langs.textmate.TextMateLanguage#create} build syntax tree theo grammar
 * tương ứng.
 *
 * <p>Unknown extension → {@code null} → caller fallback {@link
 * io.github.rosemoe.sora.lang.EmptyLanguage} (plain text, không highlight).
 *
 * <p>Special-case: Dockerfile được nhận dạng theo tên file (không có extension), không qua
 * extension map.
 */
public final class LanguageResolver {
    private static final Map<String, String> EXT_TO_SCOPE = buildMap();

    private LanguageResolver() {
    }

    /**
     * Resolve scope name theo filename. Trả {@code null} nếu không match.
     *
     * <p>Caller pass {@code File.getName()} hoặc tương đương — KHÔNG path. Match case-insensitive.
     */
    @Nullable
    public static String scopeFor(@NonNull String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        // Dockerfile (no extension) — exact match
        if ("dockerfile".equals(name) || name.endsWith(".dockerfile")) {
            return "source.dockerfile";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot + 1);
        return EXT_TO_SCOPE.get(ext);
    }

    private static Map<String, String> buildMap() {
        Map<String, String> m = new HashMap<>(64);
        m.put("java", "source.java");
        // Kotlin: bundled grammar có scope source.gradle-kotlin-dsl (VS Code Gradle DSL extension
        // EXTENDS Kotlin syntax) — vẫn highlight .kt/.kts chuẩn. Defer "real" Kotlin scope đến
        // khi có grammar source.kotlin chính chủ.
        m.put("kt", "source.gradle-kotlin-dsl");
        m.put("kts", "source.gradle-kotlin-dsl");
        m.put("gradle", "source.gradle-kotlin-dsl");
        m.put("js", "source.js");
        m.put("mjs", "source.js");
        m.put("cjs", "source.js");
        m.put("jsx", "source.js");
        m.put("ts", "source.ts");
        m.put("tsx", "source.ts");
        m.put("py", "source.python");
        m.put("pyw", "source.python");
        m.put("css", "source.css");
        m.put("html", "text.html.basic");
        m.put("htm", "text.html.basic");
        m.put("xhtml", "text.html.basic");
        m.put("xml", "text.xml");
        m.put("xsd", "text.xml");
        m.put("xsl", "text.xml");
        m.put("xslt", "text.xml");
        m.put("plist", "text.xml");
        m.put("md", "text.html.markdown");
        m.put("markdown", "text.html.markdown");
        m.put("mdown", "text.html.markdown");
        m.put("json", "source.json");
        m.put("jsonc", "source.json");
        m.put("yaml", "source.yaml");
        m.put("yml", "source.yaml");
        m.put("sh", "source.shell");
        m.put("bash", "source.shell");
        m.put("zsh", "source.shell");
        m.put("sql", "source.sql");
        m.put("c", "source.c");
        m.put("h", "source.c");
        m.put("cpp", "source.cpp");
        m.put("cc", "source.cpp");
        m.put("cxx", "source.cpp");
        m.put("hpp", "source.cpp");
        m.put("hxx", "source.cpp");
        m.put("hh", "source.cpp");
        m.put("rs", "source.rust");
        m.put("go", "source.go");
        m.put("rb", "source.ruby");
        m.put("php", "source.php");
        m.put("lua", "source.lua");
        return m;
    }
}

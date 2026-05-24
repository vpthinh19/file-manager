package com.vpt.filemanager.texteditor.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves a document name to the stable TextMate scope exposed by {@link SyntaxCatalog}.
 */
public final class LanguageResolver {
    private static final Map<String, String> EXT_TO_SCOPE = buildMap();
    private static final Map<String, String> NAME_TO_SCOPE = buildNameMap();

    private LanguageResolver() {
    }

    @Nullable
    public static String scopeFor(@NonNull String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        String named = NAME_TO_SCOPE.get(name);
        if (named != null) {
            return named;
        }
        if (name.endsWith(".dockerfile")) {
            return "source.dockerfile";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return EXT_TO_SCOPE.get(name.substring(dot + 1));
    }

    private static Map<String, String> buildNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("dockerfile", "source.dockerfile");
        map.put("makefile", "source.makefile");
        map.put("gnumakefile", "source.makefile");
        map.put(".gitignore", "source.ignore");
        map.put(".dockerignore", "source.ignore");
        map.put(".env", "source.dotenv");
        return map;
    }

    private static Map<String, String> buildMap() {
        Map<String, String> map = new HashMap<>(96);
        map.put("bat", "source.batchfile");
        map.put("cmd", "source.batchfile");
        map.put("clj", "source.clojure");
        map.put("cljs", "source.clojure");
        map.put("coffee", "source.coffee");
        map.put("c", "source.c");
        map.put("h", "source.c");
        map.put("cpp", "source.cpp");
        map.put("cc", "source.cpp");
        map.put("cxx", "source.cpp");
        map.put("hpp", "source.cpp");
        map.put("hxx", "source.cpp");
        map.put("hh", "source.cpp");
        map.put("cu", "source.cuda-cpp");
        map.put("cuh", "source.cuda-cpp");
        map.put("cs", "source.cs");
        map.put("css", "source.css");
        map.put("dart", "source.dart");
        map.put("diff", "source.diff");
        map.put("patch", "source.diff");
        map.put("env", "source.dotenv");
        map.put("fs", "source.fsharp");
        map.put("fsx", "source.fsharp");
        map.put("go", "source.go");
        map.put("groovy", "source.groovy");
        map.put("gvy", "source.groovy");
        map.put("hbs", "text.html.handlebars");
        map.put("handlebars", "text.html.handlebars");
        map.put("hlsl", "source.hlsl");
        map.put("html", "text.html.basic");
        map.put("htm", "text.html.basic");
        map.put("xhtml", "text.html.basic");
        map.put("ini", "source.ini");
        map.put("cfg", "source.ini");
        map.put("java", "source.java");
        map.put("kt", "source.gradle-kotlin-dsl");
        map.put("kts", "source.gradle-kotlin-dsl");
        map.put("gradle", "source.gradle-kotlin-dsl");
        map.put("js", "source.js");
        map.put("mjs", "source.js");
        map.put("cjs", "source.js");
        map.put("jsx", "source.js.jsx");
        map.put("json", "source.json");
        map.put("jsonc", "source.json.comments");
        map.put("jsonl", "source.json.lines");
        map.put("jl", "source.julia");
        map.put("tex", "text.tex.latex");
        map.put("bib", "text.bibtex");
        map.put("less", "source.css.less");
        map.put("lua", "source.lua");
        map.put("mk", "source.makefile");
        map.put("md", "text.html.markdown");
        map.put("markdown", "text.html.markdown");
        map.put("mdown", "text.html.markdown");
        map.put("m", "source.objc");
        map.put("mm", "source.objcpp");
        map.put("pl", "source.perl");
        map.put("pm", "source.perl");
        map.put("raku", "source.perl.6");
        map.put("php", "source.php");
        map.put("ps1", "source.powershell");
        map.put("psm1", "source.powershell");
        map.put("pug", "text.pug");
        map.put("jade", "text.pug");
        map.put("py", "source.python");
        map.put("pyw", "source.python");
        map.put("r", "source.r");
        map.put("cshtml", "text.html.cshtml");
        map.put("rst", "source.rst");
        map.put("rb", "source.ruby");
        map.put("rs", "source.rust");
        map.put("scss", "source.css.scss");
        map.put("shader", "source.shaderlab");
        map.put("shaderlab", "source.shaderlab");
        map.put("sh", "source.shell");
        map.put("bash", "source.shell");
        map.put("zsh", "source.shell");
        map.put("sql", "source.sql");
        map.put("swift", "source.swift");
        map.put("ts", "source.ts");
        map.put("tsx", "source.tsx");
        map.put("vb", "source.asp.vb.net");
        map.put("xml", "text.xml");
        map.put("xsd", "text.xml");
        map.put("plist", "text.xml");
        map.put("xsl", "text.xml.xsl");
        map.put("xslt", "text.xml.xsl");
        map.put("yaml", "source.yaml");
        map.put("yml", "source.yaml");
        return map;
    }
}

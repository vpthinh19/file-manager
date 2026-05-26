package com.vpt.filemanager.component.content.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-owned catalog of available TextMate grammars.
 *
 * <p>This catalog is intentionally data-only. It maps stable TextMate scopes to the TM4E asset
 * bundle without constructing grammar/analyzer instances. {@link SyntaxSetup} uses it as a
 * flyweight registry and loads only scopes needed by opened documents.
 */
public final class SyntaxCatalog {
    private static final Map<String, SyntaxDefinition> DEFINITIONS = createDefinitions();

    private SyntaxCatalog() {
    }

    @Nullable
    static SyntaxDefinition find(@Nullable String scopeName) {
        return scopeName == null ? null : DEFINITIONS.get(scopeName);
    }

    @NonNull
    static Collection<SyntaxDefinition> all() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    @NonNull
    public static String displayName(@Nullable String scopeName) {
        SyntaxDefinition definition = find(scopeName);
        return definition == null ? "Plain text" : definition.displayName;
    }

    private static Map<String, SyntaxDefinition> createDefinitions() {
        Map<String, SyntaxDefinition> definitions = new LinkedHashMap<>();
        add(definitions, "batch", "Batch", "source.batchfile", "bat/bat.tmLanguage.json",
                "bat/bat.language-configuration.json");
        add(definitions, "clojure", "Clojure", "source.clojure", "clojure/clojure.tmLanguage.json",
                "clojure/clojure.language-configuration.json");
        add(definitions, "coffee", "CoffeeScript", "source.coffee",
                "coffeescript/coffeescript.tmLanguage.json",
                "coffeescript/coffeescript.language-configuration.json");
        add(definitions, "c", "C", "source.c", "cpp/c.tmLanguage.json",
                "cpp/c.language-configuration.json");
        add(definitions, "cpp", "C++", "source.cpp", "cpp/cpp.tmLanguage.json",
                "cpp/cpp.language-configuration.json", "source.c");
        add(definitions, "cuda-cpp", "CUDA C++", "source.cuda-cpp", "cpp/cuda-cpp.tmLanguage.json",
                "cpp/cpp.language-configuration.json", "source.cpp", "source.c");
        add(definitions, "csharp", "C#", "source.cs", "csharp/csharp.tmLanguage.json",
                "csharp/csharp.language-configuration.json");
        add(definitions, "css", "CSS", "source.css", "css/css.tmLanguage.json",
                "css/css.language-configuration.json");
        add(definitions, "dart", "Dart", "source.dart", "dart/dart.tmLanguage.json",
                "dart/dart.language-configuration.json");
        add(definitions, "diff", "Diff", "source.diff", "diff/diff.tmLanguage.json",
                "diff/diff.language-configuration.json");
        add(definitions, "dockerfile", "Dockerfile", "source.dockerfile",
                "docker/dockerfile.tmLanguage.json", "docker/dockerfile.language-configuration.json");
        add(definitions, "dotenv", "Dotenv", "source.dotenv", "dotenv/dotenv.tmLanguage.json",
                "dotenv/dotenv.language-configuration.json");
        add(definitions, "fsharp", "F#", "source.fsharp", "fsharp/fsharp.tmLanguage.json",
                "fsharp/fsharp.language-configuration.json");
        add(definitions, "git-commit", "Git commit", "text.git-commit",
                "git-base/git-commit.tmLanguage.json", "git-base/git-commit.language-configuration.json");
        add(definitions, "git-rebase", "Git rebase", "text.git-rebase",
                "git-base/git-rebase.tmLanguage.json", "git-base/git-rebase.language-configuration.json");
        add(definitions, "ignore", "Ignore", "source.ignore", "git-base/ignore.tmLanguage.json", null);
        add(definitions, "go", "Go", "source.go", "go/go.tmLanguage.json",
                "go/go.language-configuration.json");
        add(definitions, "groovy", "Groovy", "source.groovy", "groovy/groovy.tmLanguage.json",
                "groovy/groovy.language-configuration.json");
        add(definitions, "handlebars", "Handlebars", "text.html.handlebars",
                "handlebars/handlebars.tmLanguage.json", "handlebars/handlebars.language-configuration.json",
                "text.html.basic", "source.css", "source.js");
        add(definitions, "hlsl", "HLSL", "source.hlsl", "hlsl/hlsl.tmLanguage.json",
                "hlsl/hlsl.language-configuration.json");
        add(definitions, "html", "HTML", "text.html.basic", "html/text.html.basic.tmLanguage.json",
                "html/html.language-configuration.json", "source.css", "source.js");
        add(definitions, "ini", "INI", "source.ini", "ini/ini.tmLanguage.json",
                "ini/ini.language-configuration.json");
        add(definitions, "java", "Java", "source.java", "java/java.tmLanguage.json",
                "java/java.language-configuration.json");
        addExternal(definitions, "kotlin", "Kotlin", "source.gradle-kotlin-dsl",
                "editor/textmate/grammars/kotlin.tmLanguage.json", null);
        add(definitions, "javascript", "JavaScript", "source.js",
                "javascript/javascript.tmLanguage.json", "javascript/javascript.language-configuration.json");
        add(definitions, "jsx", "JSX", "source.js.jsx",
                "javascript/javascriptreact.tmLanguage.json",
                "javascript/javascriptreact.language-configuration.json", "source.js", "text.html.basic");
        add(definitions, "jikespg", "JikesPG", "source.jikespg", "jikespg/jikespg.tmLanguage.json", null);
        add(definitions, "json", "JSON", "source.json", "json/json.tmLanguage.json",
                "json/json.language-configuration.json");
        add(definitions, "jsonc", "JSON with comments", "source.json.comments",
                "json/jsonc.tmLanguage.json", "json/jsonc.language-configuration.json");
        add(definitions, "jsonl", "JSON Lines", "source.json.lines", "json/jsonl.tmLanguage.json",
                "json/json.language-configuration.json");
        add(definitions, "julia", "Julia", "source.julia", "julia/julia.tmLanguage.json",
                "julia/julia.language-configuration.json");
        add(definitions, "latex", "LaTeX", "text.tex.latex", "latex/latex.tmLanguage.json",
                "latex/latex.language-configuration.json");
        add(definitions, "tex", "TeX", "text.tex", "latex/tex.tmLanguage.json",
                "latex/latex.language-configuration.json");
        add(definitions, "bibtex", "BibTeX", "text.bibtex", "latex/bibtex.tmLanguage.json",
                "latex/latex.language-configuration.json");
        add(definitions, "less", "Less", "source.css.less", "less/less.tmLanguage.json",
                "less/less.language-configuration.json", "source.css");
        add(definitions, "lua", "Lua", "source.lua", "lua/lua.tmLanguage.json",
                "lua/lua.language-configuration.json");
        add(definitions, "makefile", "Makefile", "source.makefile", "make/makefile.tmLanguage.json",
                "make/makefile.language-configuration.json");
        add(definitions, "markdown", "Markdown", "text.html.markdown",
                "markdown/markdown.tmLanguage.json", "markdown/markdown.language-configuration.json",
                "text.html.basic");
        add(definitions, "objc", "Objective-C", "source.objc",
                "objective-c/objective-c.tmLanguage.json",
                "objective-c/objective-c.language-configuration.json", "source.c");
        add(definitions, "objcpp", "Objective-C++", "source.objcpp",
                "objective-c/objective-cpp.tmLanguage.json",
                "objective-c/objective-cpp.language-configuration.json", "source.cpp", "source.c");
        add(definitions, "perl", "Perl", "source.perl", "perl/perl.tmLanguage.json",
                "perl/perl.language-configuration.json");
        add(definitions, "raku", "Raku", "source.perl.6", "perl/raku.tmLanguage.json",
                "perl/raku.language-configuration.json");
        add(definitions, "php", "PHP", "source.php", "php/php.tmLanguage.json",
                "php/php.language-configuration.json", "text.html.basic", "source.css", "source.js");
        add(definitions, "powershell", "PowerShell", "source.powershell",
                "powershell/powershell.tmLanguage.json", "powershell/powershell.language-configuration.json");
        add(definitions, "pug", "Pug", "text.pug", "pug/jade.tmLanguage.json",
                "pug/jade.language-configuration.json", "source.js", "source.css");
        add(definitions, "python", "Python", "source.python", "python/python.tmLanguage.json",
                "python/python.language-configuration.json");
        add(definitions, "r", "R", "source.r", "r/r.tmLanguage.json",
                "r/r.language-configuration.json");
        add(definitions, "razor", "Razor", "text.html.cshtml", "razor/razor.tmLanguage.json",
                "razor/razor.language-configuration.json", "text.html.basic", "source.css", "source.js");
        add(definitions, "rst", "reStructuredText", "source.rst",
                "restructuredtext/restructuredtext.tmLanguage.json",
                "restructuredtext/restructuredtext.language-configuration.json");
        add(definitions, "ruby", "Ruby", "source.ruby", "ruby/ruby.tmLanguage.json",
                "ruby/ruby.language-configuration.json");
        add(definitions, "rust", "Rust", "source.rust", "rust/rust.tmLanguage.json",
                "rust/rust.language-configuration.json");
        add(definitions, "scss", "SCSS", "source.css.scss", "scss/scss.tmLanguage.json",
                "scss/scss.language-configuration.json", "source.css");
        add(definitions, "shaderlab", "ShaderLab", "source.shaderlab",
                "shaderlab/shaderlab.tmLanguage.json", "shaderlab/shaderlab.language-configuration.json");
        add(definitions, "shellscript", "Shell", "source.shell",
                "shellscript/shellscript.tmLanguage.json",
                "shellscript/shellscript.language-configuration.json");
        add(definitions, "sql", "SQL", "source.sql", "sql/sql.tmLanguage.json",
                "sql/sql.language-configuration.json");
        add(definitions, "swift", "Swift", "source.swift", "swift/swift.tmLanguage.json",
                "swift/swift.language-configuration.json");
        add(definitions, "typescript", "TypeScript", "source.ts",
                "typescript/typescript.tmLanguage.json", "typescript/typescript.language-configuration.json",
                "source.js");
        add(definitions, "tsx", "TSX", "source.tsx",
                "typescript/typescriptreact.tmLanguage.json",
                "typescript/typescriptreact.language-configuration.json", "source.ts", "source.js",
                "text.html.basic");
        add(definitions, "vb", "Visual Basic", "source.asp.vb.net", "vb/vb.tmLanguage.json",
                "vb/vb.language-configuration.json");
        add(definitions, "xml", "XML", "text.xml", "xml/xml.tmLanguage.json",
                "xml/xml.language-configuration.json");
        add(definitions, "xsl", "XSL", "text.xml.xsl", "xml/xsl.tmLanguage.json",
                "xml/xsl.language-configuration.json", "text.xml");
        add(definitions, "yaml", "YAML", "source.yaml", "yaml/yaml.tmLanguage.json",
                "yaml/yaml.language-configuration.json");
        return definitions;
    }

    private static void add(
            Map<String, SyntaxDefinition> definitions,
            String name,
            String displayName,
            String scopeName,
            String grammar,
            @Nullable String configuration,
            String... dependencies) {
        definitions.put(scopeName, new SyntaxDefinition(
                name,
                displayName,
                scopeName,
                "syntaxes/" + grammar,
                configuration == null ? null : "syntaxes/" + configuration,
                List.of(dependencies)));
    }

    private static void addExternal(
            Map<String, SyntaxDefinition> definitions,
            String name,
            String displayName,
            String scopeName,
            String grammar,
            @Nullable String configuration,
            String... dependencies) {
        definitions.put(scopeName, new SyntaxDefinition(
                name,
                displayName,
                scopeName,
                grammar,
                configuration,
                List.of(dependencies)));
    }
}

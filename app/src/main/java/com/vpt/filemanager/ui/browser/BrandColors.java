package com.vpt.filemanager.ui.browser;

import androidx.annotation.ColorRes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.vpt.filemanager.R;

/**
 * Maps a file extension to its "brand" color so {@code .py} looks Python-blue, {@code .docx}
 * looks Word-blue, etc. Used by {@link FileIconView#bindExtText} together with the file-shape
 * vector + an uppercase extension label.
 *
 * <p>Single source of truth for per-extension visual identity — adding a new mapping is one entry
 * here + one pair of color tokens (light + dark). Pure dispatch, no Android UI imports beyond the
 * resource annotations.
 */
public final class BrandColors {
    private BrandColors() {
    }

    private static final int MAX_LABEL_LEN = 4;

    private static final Map<String, Integer> MAP = buildMap();

    private static Map<String, Integer> buildMap() {
        Map<String, Integer> m = new HashMap<>();
        // Languages
        m.put("py", R.color.brand_python);
        m.put("java", R.color.brand_java);
        m.put("kt", R.color.brand_kotlin);
        m.put("kts", R.color.brand_kotlin);
        m.put("js", R.color.brand_js);
        m.put("mjs", R.color.brand_js);
        m.put("cjs", R.color.brand_js);
        m.put("ts", R.color.brand_ts);
        m.put("tsx", R.color.brand_ts);
        m.put("jsx", R.color.brand_js);
        m.put("go", R.color.brand_go);
        m.put("rs", R.color.brand_rust);
        m.put("rb", R.color.brand_ruby);
        m.put("php", R.color.brand_php);
        m.put("c", R.color.brand_c_lang);
        m.put("h", R.color.brand_c_lang);
        m.put("cpp", R.color.brand_cpp);
        m.put("cxx", R.color.brand_cpp);
        m.put("cc", R.color.brand_cpp);
        m.put("hpp", R.color.brand_cpp);
        m.put("cs", R.color.brand_csharp);
        // Web
        m.put("html", R.color.brand_html);
        m.put("htm", R.color.brand_html);
        m.put("css", R.color.brand_css);
        m.put("scss", R.color.brand_css);
        m.put("sass", R.color.brand_css);
        m.put("less", R.color.brand_css);
        // Data
        m.put("json", R.color.brand_json);
        m.put("xml", R.color.brand_xml);
        m.put("yaml", R.color.brand_yaml);
        m.put("yml", R.color.brand_yaml);
        m.put("toml", R.color.brand_yaml);
        // Shell + db
        m.put("sh", R.color.brand_sh);
        m.put("bash", R.color.brand_sh);
        m.put("zsh", R.color.brand_sh);
        m.put("fish", R.color.brand_sh);
        m.put("sql", R.color.brand_sql);
        // Docs
        m.put("md", R.color.brand_md);
        m.put("markdown", R.color.brand_md);
        // Office
        m.put("doc", R.color.brand_word);
        m.put("docx", R.color.brand_word);
        m.put("odt", R.color.brand_word);
        m.put("rtf", R.color.brand_word);
        m.put("xls", R.color.brand_excel);
        m.put("xlsx", R.color.brand_excel);
        m.put("ods", R.color.brand_excel);
        m.put("csv", R.color.brand_excel);
        m.put("ppt", R.color.brand_ppt);
        m.put("pptx", R.color.brand_ppt);
        m.put("odp", R.color.brand_ppt);
        return m;
    }

    /** @return color resource for {@code fileName}'s extension, or {@code brand_default} if unknown. */
    @ColorRes
    public static int colorFor(String fileName) {
        String ext = extensionOf(fileName);
        if (ext == null) {
            return R.color.brand_default;
        }
        Integer color = MAP.get(ext);
        return color != null ? color : R.color.brand_default;
    }

    /**
     * @return uppercase extension truncated to {@value #MAX_LABEL_LEN} chars, or {@code "?"} when
     * the name has no extension.
     */
    public static String label(String fileName) {
        String ext = extensionOf(fileName);
        if (ext == null) {
            return "?";
        }
        String upper = ext.toUpperCase(Locale.US);
        return upper.length() > MAX_LABEL_LEN ? upper.substring(0, MAX_LABEL_LEN) : upper;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.US);
    }
}

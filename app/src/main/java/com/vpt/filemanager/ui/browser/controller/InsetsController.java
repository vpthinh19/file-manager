package com.vpt.filemanager.ui.browser.controller;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;

/**
 * Áp dụng WindowInsets cho bottom container (nav bar inset) + appbar (status bar inset).
 *
 * <p><b>Edge-to-edge pattern</b>: AppBarLayout consume top inset, pad toolbar xuống tránh status
 * bar. Bottom container pad lên tránh gesture/nav bar. Layer B status bar fix (Phase A) =
 * fitsSystemWindows trên AppBarLayout + windowBackground=chrome → toolbar bg paint cả status bar
 * area.
 */
public final class InsetsController {
    private final FragmentDualPaneHostBinding binding;

    public InsetsController(FragmentDualPaneHostBinding binding) {
        this.binding = binding;
    }

    public void attach() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomContainer, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(binding.appbar, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            binding.toolbar.setPadding(
                    binding.toolbar.getPaddingLeft(),
                    statusBarHeight,
                    binding.toolbar.getPaddingRight(),
                    binding.toolbar.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.appbar);
    }
}

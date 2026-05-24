package com.vpt.filemanager.browser.ui.pane.controller;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.vpt.filemanager.databinding.FragmentDualPaneHostBinding;

/** Applies status and navigation bar insets to the pane chrome. */
public final class InsetsController {
    private final FragmentDualPaneHostBinding binding;

    public InsetsController(FragmentDualPaneHostBinding binding) {
        this.binding = binding;
    }

    public void attach() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomContainer, (view, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                    bottom);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(binding.appbar, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            binding.toolbar.setPadding(binding.toolbar.getPaddingLeft(), top,
                    binding.toolbar.getPaddingRight(), binding.toolbar.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.appbar);
    }
}

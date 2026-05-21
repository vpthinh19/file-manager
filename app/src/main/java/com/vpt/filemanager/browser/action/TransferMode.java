package com.vpt.filemanager.browser.action;

/**
 * Phase C-1b: mode discriminator cho cross-pane transfer. Enum thay vì 2 method tách biệt để
 * {@link TransferAction#execute(TransferMode)} là single entry point + result format share
 * verb ("copied" / "moved").
 */
public enum TransferMode {
    COPY,
    MOVE
}

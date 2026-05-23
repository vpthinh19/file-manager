package com.vpt.filemanager.node.opener;

import android.content.Intent;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.format.FileCategory;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.ui.editor.TextEditorActivity;

/**
 * Mở file dạng text-based (TXT, MD, JSON, XML, source code, ...) trong sora-editor.
 *
 * <p>Match cả {@link FileCategory#TEXT} (plain text + markup) và {@link FileCategory#CODE}
 * (programming language source) — cùng được editor handle như nhau. Syntax highlight per
 * language sẽ wire ở Phase R-8 qua {@code LanguageResolver}.
 *
 * <p><b>Archive entry</b>: supported through workspace {@code DocumentSession}; editor I/O stays
 * stream-based and {@code ArchiveSource} commits the enclosing container.
 *
 * <p><b>Safe-load</b>: workspace {@code DocumentSession} owns size checks, binary detection,
 * decoding, savepoints and conflict handling. Opener does not duplicate that logic.
 */
@Singleton
public final class TextOpener implements NodeOpener {

    @Inject
    public TextOpener() {
    }

    @Override
    public boolean canOpen(VirtualNode node) {
        if (node.isFolder()) {
            return false;
        }
        FileCategory cat = FileCategory.ofExtension(node.name());
        return cat == FileCategory.TEXT || cat == FileCategory.CODE;
    }

    @Override
    public void onOpen(VirtualNode node, OpenContext ctx) throws NodeException {
        NodePath path = node.path();
        Intent intent = new Intent(ctx.context(), TextEditorActivity.class);
        intent.putExtra(TextEditorActivity.EXTRA_PATH, path.toString());
        ctx.context().startActivity(intent);
    }
}

package com.vpt.filemanager.node.opener;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import androidx.annotation.Nullable;

import com.vpt.filemanager.node.VirtualNode;

/**
 * Chọn {@link NodeOpener} cho một {@link VirtualNode} theo priority list. Loop tuần tự, opener
 * đầu tiên có {@link NodeOpener#canOpen(VirtualNode)} trả {@code true} sẽ được dùng.
 *
 * <p><b>Priority order</b> (v1):
 * <ol>
 *   <li>{@link TextOpener} — text + code (in-app editor)</li>
 *   <li>{@link ArchiveOpener} — zip + jar + apk (in-app navigate)</li>
 *   <li>{@link ExternalOpener} — fallback ACTION_VIEW (chỉ local)</li>
 * </ol>
 * Phase 2D sẽ thêm Image/Video/AudioOpener TRƯỚC ExternalOpener (image trước archive nếu cần
 * tinh chỉnh).
 *
 * <p><b>Trả null</b>: khi không opener nào match (vd archive entry không phải text + ExternalOpener
 * không handle archive scheme). Caller phải null-check + show "OpenAs" dialog hoặc toast. KHÔNG
 * throw để caller có thể fallback graceful.
 */
@Singleton
public final class OpenerRegistry {
    private final List<NodeOpener> ordered;

    @Inject
    public OpenerRegistry(
            TextOpener textOpener,
            ArchiveOpener archiveOpener,
            ExternalOpener externalOpener) {
        // Specific in-app openers trước, system fallback cuối.
        // Phase 2D: insert Image/Video/Audio TRƯỚC ExternalOpener khi wire Glide/Media3.
        this.ordered = List.of(textOpener, archiveOpener, externalOpener);
    }

    /**
     * Tìm opener đầu tiên match. {@code null} nếu không có (vd archive entry không phải text).
     */
    @Nullable
    public NodeOpener openerFor(VirtualNode node) {
        for (NodeOpener opener : ordered) {
            if (opener.canOpen(node)) {
                return opener;
            }
        }
        return null;
    }
}

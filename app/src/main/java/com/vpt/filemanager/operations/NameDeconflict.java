package com.vpt.filemanager.operations;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Tiny helper: when "Keep both" is chosen for a name conflict, derive a unique name by appending
 * " (1)", " (2)", … to the base portion (preserving the extension). KISS, used by both create and
 * copy/move flows.
 *
 * <p>Works through the virtual node tree, so create and transfer flows do not need local-only
 * {@code File.exists()} checks.
 */
public final class NameDeconflict {
    private NameDeconflict() {
    }

    private static final int MAX_ATTEMPTS = 1000;

    /**
     * Cross-source variant: scan {@link VirtualNode#children()} một lần → {@link HashSet} O(N),
     * sau đó probe O(1) per candidate. NodeSource hiện không có {@code exists()} reliable nên
     * approach scan-then-probe match DOM ảo model + work cho mọi writable source future.
     *
     * @param dstParent folder đích (phải writable — caller đã validate)
     * @param baseName  tên gốc người dùng đang cố ghi
     * @return baseName nếu chưa có; ngược lại "base (N).ext" với N nhỏ nhất ≥ 1 không trùng
     */
    @NonNull
    public static String uniqueName(@NonNull VirtualNode dstParent, @NonNull String baseName)
            throws NodeException {
        List<VirtualNode> children = dstParent.children();
        Set<String> taken = new HashSet<>(children.size() * 2);
        for (VirtualNode c : children) {
            taken.add(c.name());
        }
        if (!taken.contains(baseName)) {
            return baseName;
        }
        int dot = baseName.lastIndexOf('.');
        String base = dot <= 0 ? baseName : baseName.substring(0, dot);
        String ext = dot <= 0 ? "" : baseName.substring(dot);
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return base + "_" + System.currentTimeMillis() + ext;
    }
}

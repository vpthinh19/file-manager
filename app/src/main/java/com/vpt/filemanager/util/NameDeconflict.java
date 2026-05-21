package com.vpt.filemanager.util;

import androidx.annotation.NonNull;

import java.io.File;
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
 * <p>2 overloads:
 * <ul>
 *   <li>{@link #unique(File, String)} — local-only, dùng cho CreateAction (current folder always
 *       local via guard isLocal()).</li>
 *   <li>{@link #uniqueName(VirtualNode, String)} — cross-source via DOM ảo, dùng cho Phase C-1b
 *       transfer (destination có thể là pane khác với scheme khác).</li>
 * </ul>
 */
public final class NameDeconflict {
    private NameDeconflict() {
    }

    private static final int MAX_ATTEMPTS = 1000;

    @NonNull
    public static String unique(@NonNull File dir, @NonNull String name) {
        if (!new File(dir, name).exists()) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot <= 0 ? name : name.substring(0, dot);
        String ext = dot <= 0 ? "" : name.substring(dot);
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!new File(dir, candidate).exists()) {
                return candidate;
            }
        }
        return base + "_" + System.currentTimeMillis() + ext;
    }

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

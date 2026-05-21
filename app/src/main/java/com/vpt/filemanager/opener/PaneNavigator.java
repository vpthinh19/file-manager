package com.vpt.filemanager.opener;

import com.vpt.filemanager.node.FilePath;

/**
 * Khả năng "navigate pane sang một path khác". Được expose qua {@link OpenContext} cho các
 * opener (chủ yếu là {@link ArchiveOpener}) gọi mà không cần biết PaneViewModel cụ thể.
 *
 * <p>R-5b sửa từ {@code navigateTo(VirtualNode)} → {@code navigateTo(FilePath)}: caller chỉ cần
 * path identity; PaneViewModel sẽ tự re-resolve qua NodeFactory. Loose coupling: opener không
 * giữ tham chiếu node có thể stale, PaneNavigator không phụ thuộc node module hierarchy.
 *
 * <p>BrowserFragment (R-5b) impl bằng lambda {@code vm::navigateTo}.
 */
public interface PaneNavigator {
    void navigateTo(FilePath path);
}

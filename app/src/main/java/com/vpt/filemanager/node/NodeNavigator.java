package com.vpt.filemanager.node;

import com.vpt.filemanager.node.opener.ArchiveOpener;
import com.vpt.filemanager.node.opener.OpenContext;

/**
 * Khả năng "navigate pane sang một path khác". Được expose qua {@link OpenContext} cho các
 * opener (chủ yếu là {@link ArchiveOpener}) gọi mà không cần biết PaneViewModel cụ thể.
 *
 * <p>R-5b sửa từ {@code navigateTo(VirtualNode)} → {@code navigateTo(NodePath)}: caller chỉ cần
 * path identity; PaneViewModel sẽ tự re-resolve qua NodeFactory. Loose coupling: opener không
 * giữ tham chiếu node có thể stale, NodeNavigator không phụ thuộc node module hierarchy.
 *
 * <p>BrowserFragment (R-5b) impl bằng lambda {@code vm::navigateTo}.
 */
public interface NodeNavigator {
    void navigateTo(NodePath path);
}

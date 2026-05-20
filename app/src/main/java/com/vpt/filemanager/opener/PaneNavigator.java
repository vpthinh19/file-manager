package com.vpt.filemanager.opener;

import com.vpt.filemanager.node.VirtualNode;

/**
 * Khả năng "navigate pane sang một VirtualNode khác". Được expose qua {@link OpenContext} cho
 * các opener (chủ yếu là {@link ArchiveOpener}) gọi mà không cần biết PaneViewModel cụ thể.
 *
 * <p>Phase R-5 sẽ implement: BrowserFragment tạo {@link OpenContext} với lambda
 * {@code activeVm()::navigateTo} làm pane navigator. Tách interface để opener không phụ thuộc
 * vào lớp UI cụ thể — dễ test, dễ refactor.
 */
public interface PaneNavigator {
    void navigateTo(VirtualNode node);
}

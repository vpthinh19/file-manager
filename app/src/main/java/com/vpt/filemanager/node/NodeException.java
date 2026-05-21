package com.vpt.filemanager.node;

import com.vpt.filemanager.error.AppException;

/**
 * Lỗi xảy ra khi tương tác với cây {@link VirtualNode} — list children, read entry, hoặc khi
 * source backend (local FS, archive, Room) trả về failure. Bubble lên ViewModel → ErrorPresenter
 * format ra Toast/Dialog. Caller-boundary catch quy ước trong project (xem ARCHITECTURE.md).
 */
public class NodeException extends AppException {
    public NodeException(String message) {
        super(message);
    }

    public NodeException(String message, Throwable cause) {
        super(message, cause);
    }
}

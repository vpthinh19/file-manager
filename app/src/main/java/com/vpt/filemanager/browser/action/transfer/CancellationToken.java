package com.vpt.filemanager.browser.action.transfer;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get(); }
}

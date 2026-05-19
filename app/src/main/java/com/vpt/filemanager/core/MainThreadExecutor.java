package com.vpt.filemanager.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

final class MainThreadExecutor implements Executor {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(Runnable command) {
        handler.post(command);
    }
}


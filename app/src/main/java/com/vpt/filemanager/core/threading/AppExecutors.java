package com.vpt.filemanager.core.threading;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class AppExecutors {
    private final ExecutorService io;
    private final ExecutorService computation;
    private final Executor main;

    @Inject
    public AppExecutors() {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.io = Executors.newFixedThreadPool(Math.min(8, cores * 2));
        this.computation = Executors.newFixedThreadPool(cores);
        this.main = new MainThreadExecutor();
    }

    public ExecutorService io() {
        return io;
    }

    public ExecutorService computation() {
        return computation;
    }

    public Executor main() {
        return main;
    }
}


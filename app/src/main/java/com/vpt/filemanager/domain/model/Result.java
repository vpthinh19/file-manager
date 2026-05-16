package com.vpt.filemanager.domain.model;

public abstract class Result<T> {
    public static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    public static <T> Result<T> failure(Throwable error) {
        return new Failure<>(error);
    }

    public abstract boolean isSuccess();

    public abstract T getOrThrow() throws Throwable;

    public abstract T getOrNull();

    public abstract Throwable errorOrNull();

    public static final class Success<T> extends Result<T> {
        private final T value;

        private Success(T value) {
            this.value = value;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T getOrThrow() {
            return value;
        }

        @Override
        public T getOrNull() {
            return value;
        }

        @Override
        public Throwable errorOrNull() {
            return null;
        }
    }

    public static final class Failure<T> extends Result<T> {
        private final Throwable error;

        private Failure(Throwable error) {
            this.error = error;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T getOrThrow() throws Throwable {
            throw error;
        }

        @Override
        public T getOrNull() {
            return null;
        }

        @Override
        public Throwable errorOrNull() {
            return error;
        }
    }
}


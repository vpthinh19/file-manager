package com.vpt.filemanager.util;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;

import com.vpt.filemanager.R;
import com.vpt.filemanager.error.ArchiveException;
import com.vpt.filemanager.error.FileSystemException;

/**
 * Maps a {@link Throwable} thrown at a UI boundary to a user-friendly message and renders it via
 * Toast or AlertDialog. Centralises the strings + UX so every caller (Activity / Fragment) shows
 * consistent error text.
 *
 * <p>Mapping lives in {@link Kind} — a Strategy table implemented as an enum so the match-and-map
 * pair stays adjacent and adding a new error type is one line. The list is ordered: the first
 * matching {@code Kind} wins, so more specific types (Binary, Archive) precede their supertypes
 * (FileSystemException → IOException → Throwable).
 */
public final class ErrorPresenter {
    private ErrorPresenter() {
    }

    private enum Kind {
        ARCHIVE   (ArchiveException.class,     R.string.error_archive_corrupt),
        PERMISSION(SecurityException.class,    R.string.error_permission_denied),
        FS        (FileSystemException.class,  R.string.error_io),
        IO        (IOException.class,          R.string.error_io),
        OOM       (OutOfMemoryError.class,     R.string.error_file_too_large),
        UNKNOWN   (Throwable.class,            R.string.error_unknown);

        final Class<?> type;
        @StringRes final int messageRes;

        Kind(Class<?> type, @StringRes int messageRes) {
            this.type = type;
            this.messageRes = messageRes;
        }

        static Kind match(@NonNull Throwable t) {
            Throwable probe = t;
            // Unwrap one level: VM wraps use-case throwables in events but Java IO commonly hides
            // SecurityException as the cause of an IOException. Surface the most specific kind.
            while (probe != null) {
                for (Kind k : values()) {
                    if (k.type.isInstance(probe)) {
                        return k;
                    }
                }
                probe = probe.getCause();
            }
            return UNKNOWN;
        }
    }

    public static void toast(@NonNull Context ctx, @NonNull Throwable t) {
        Toast.makeText(ctx, userMessage(ctx, t), Toast.LENGTH_LONG).show();
    }

    public static void dialog(@NonNull Context ctx, @StringRes int titleRes,
                              @NonNull Throwable t) {
        new AlertDialog.Builder(ctx)
                .setTitle(titleRes)
                .setMessage(userMessage(ctx, t))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @NonNull
    public static String userMessage(@NonNull Context ctx, @NonNull Throwable t) {
        return ctx.getString(Kind.match(t).messageRes);
    }
}

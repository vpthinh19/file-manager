package com.vpt.filemanager.core.error;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;

import com.vpt.filemanager.R;

/** Renders failures crossing into Android UI as stable user-facing messages. */
public final class ErrorPresenter {
    private ErrorPresenter() {
    }

    private enum Kind {
        PERMISSION(SecurityException.class, R.string.error_permission_denied),
        FILE(FileOperationException.class, R.string.error_io),
        IO(IOException.class, R.string.error_io),
        OOM(OutOfMemoryError.class, R.string.error_file_too_large),
        UNKNOWN(Throwable.class, R.string.error_unknown);

        final Class<?> type;
        @StringRes final int messageRes;

        Kind(Class<?> type, @StringRes int messageRes) {
            this.type = type;
            this.messageRes = messageRes;
        }

        static Kind match(@NonNull Throwable error) {
            Throwable probe = error;
            while (probe != null) {
                for (Kind kind : values()) {
                    if (kind.type.isInstance(probe)) {
                        return kind;
                    }
                }
                probe = probe.getCause();
            }
            return UNKNOWN;
        }
    }

    public static void toast(@NonNull Context context, @NonNull Throwable error) {
        Toast.makeText(context, userMessage(context, error), Toast.LENGTH_LONG).show();
    }

    public static void dialog(@NonNull Context context, @StringRes int titleRes,
                              @NonNull Throwable error) {
        new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setMessage(userMessage(context, error))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @NonNull
    public static String userMessage(@NonNull Context context, @NonNull Throwable error) {
        return context.getString(Kind.match(error).messageRes);
    }
}

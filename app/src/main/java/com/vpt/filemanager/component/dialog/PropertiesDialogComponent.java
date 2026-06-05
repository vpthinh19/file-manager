package com.vpt.filemanager.component.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vpt.filemanager.R;
import com.vpt.filemanager.app.threading.AppExecutors;
import com.vpt.filemanager.core.entry.Entry;
import com.vpt.filemanager.core.format.ByteSize;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Read-only file metadata, MT Manager style. The active row set degrades gracefully: anything that
 * needs the physical path or a {@code stat()} syscall is simply skipped when unavailable, so the
 * dialog never fails the caller. Layout is a monospace {@link TextView} for column alignment.
 */
public final class PropertiesDialogComponent {
    private static final int LABEL_WIDTH = 12;
    private static final DateFormat DATE =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

    private PropertiesDialogComponent() {
    }

    /** Convenience overload without checksum support (no background executor available). */
    public static void show(@NonNull Context context, @NonNull Entry entry) {
        show(context, entry, null);
    }

    /** When {@code executors} is non-null, files (not folders) gain a Checksum button. */
    public static void show(@NonNull Context context, @NonNull Entry entry,
                            @Nullable AppExecutors executors) {
        String physical = entry.localPathOrNull();
        StringBuilder body = new StringBuilder();
        append(body, "Name", entry.name());
        if (physical != null) {
            int slash = physical.lastIndexOf('/');
            if (slash > 0) append(body, "Parent", physical.substring(0, slash + 1));
        }
        append(body, "Type", entry.isFolder() ? "Folder" : "File");
        append(body, "Size", formatSize(entry.size()));
        if (entry.modifiedAt() > 0) append(body, "Modified", DATE.format(new Date(entry.modifiedAt())));
        if (physical != null) appendStat(body, physical);

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.properties)
                .setView(monospaceView(context, body.toString().trim()))
                .setPositiveButton(android.R.string.ok, null);
        if (executors != null && physical != null && !entry.isFolder()) {
            String path = physical;
            builder.setNeutralButton(R.string.action_checksum,
                    (dialog, which) -> computeChecksum(context, path, executors));
        }
        builder.show();
    }

    /** Appends Permissions / Owner / Group from a single stat() call; skipped on any error. */
    private static void appendStat(StringBuilder body, String path) {
        try {
            StructStat stat = Os.stat(path);
            append(body, "Permissions", formatMode(stat.st_mode));
            append(body, "Owner", String.valueOf(stat.st_uid));
            append(body, "Group", String.valueOf(stat.st_gid));
        } catch (ErrnoException ignored) {
            // Metadata is optional; the rest of the dialog still renders.
        }
    }

    /** Renders an octal+symbolic mode like {@code -rwxrwx---(770)}. */
    private static String formatMode(int mode) {
        char[] symbolic = new char[10];
        symbolic[0] = OsConstants.S_ISDIR(mode) ? 'd' : OsConstants.S_ISLNK(mode) ? 'l' : '-';
        String flags = "rwxrwxrwx";
        for (int i = 0; i < 9; i++) {
            symbolic[i + 1] = (mode & (1 << (8 - i))) != 0 ? flags.charAt(i) : '-';
        }
        return new String(symbolic) + "(" + String.format(Locale.US, "%03o", mode & 0777) + ")";
    }

    private static String formatSize(long bytes) {
        if (bytes < 0) return "Unknown";
        return ByteSize.format(bytes) + " (" + bytes + " bytes)";
    }

    private static void computeChecksum(Context context, String path, AppExecutors executors) {
        AlertDialog progress = new AlertDialog.Builder(context)
                .setTitle(R.string.action_checksum)
                .setMessage(R.string.checksum_calculating)
                .setCancelable(true)
                .show();
        executors.io().execute(() -> {
            String result;
            try {
                result = "MD5\n" + digest(path, "MD5")
                        + "\n\nSHA-256\n" + digest(path, "SHA-256");
            } catch (Exception error) {
                result = "Error: " + error.getMessage();
            }
            String computed = result;
            executors.main().execute(() -> {
                progress.dismiss();
                if (context instanceof Activity activity
                        && (activity.isFinishing() || activity.isDestroyed())) {
                    return;
                }
                new AlertDialog.Builder(context)
                        .setTitle(R.string.action_checksum)
                        .setView(monospaceView(context, computed))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        });
    }

    private static String digest(String path, String algorithm) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] buffer = new byte[8192];
        try (InputStream in = new FileInputStream(path)) {
            int read;
            while ((read = in.read(buffer)) != -1) md.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : md.digest()) hex.append(String.format(Locale.US, "%02x", value));
        return hex.toString();
    }

    private static void append(StringBuilder body, String label, String value) {
        body.append(padRight(label)).append(value).append('\n');
    }

    private static String padRight(String label) {
        StringBuilder padded = new StringBuilder(label);
        while (padded.length() < LABEL_WIDTH) padded.append(' ');
        return padded.toString();
    }

    private static TextView monospaceView(Context context, String text) {
        TextView view = new TextView(context);
        int side = dp(context, 24);
        view.setPadding(side, dp(context, 16), side, 0);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        view.setText(text);
        return view;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }
}

package com.vpt.filemanager.component.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

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
 * Read-only file metadata, MT Manager style. Rows use the {@code PropertiesLabel}/{@code
 * PropertiesValue} theme styles (app font + Material colors) so the dialog stays visually in sync
 * with the rest of the app. Anything needing the physical path or a {@code stat()} syscall is
 * skipped when unavailable, so the dialog never fails the caller.
 */
public final class PropertiesDialogComponent {
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
        LinearLayout rows = column(context);
        addRow(rows, "Name", entry.name());
        if (physical != null) {
            int slash = physical.lastIndexOf('/');
            if (slash > 0) addRow(rows, "Parent", physical.substring(0, slash + 1));
        }
        addRow(rows, "Type", entry.isFolder() ? "Folder" : "File");
        addRow(rows, "Size", formatSize(entry.size()));
        if (entry.modifiedAt() > 0) addRow(rows, "Modified", DATE.format(new Date(entry.modifiedAt())));
        if (physical != null) addStatRows(rows, physical);

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(R.string.properties)
                .setView(rows)
                .setPositiveButton(android.R.string.ok, null);
        if (executors != null && physical != null && !entry.isFolder()) {
            String path = physical;
            builder.setNeutralButton(R.string.action_checksum,
                    (dialog, which) -> computeChecksum(context, path, executors));
        }
        builder.show();
    }

    /** Appends Permissions / Owner / Group from a single stat() call; skipped on any error. */
    private static void addStatRows(LinearLayout rows, String path) {
        try {
            StructStat stat = Os.stat(path);
            addRow(rows, "Permissions", formatMode(stat.st_mode));
            addRow(rows, "Owner", String.valueOf(stat.st_uid));
            addRow(rows, "Group", String.valueOf(stat.st_gid));
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
                        .setView(messageView(context, computed))
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

    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int side = dp(context, 24);
        layout.setPadding(side, dp(context, 12), side, 0);
        return layout;
    }

    /** Adds one {@code label  …  value} row using the shared Properties styles. */
    private static void addRow(LinearLayout parent, String label, String value) {
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int gap = dp(context, 6);
        row.setPadding(0, gap, 0, gap);

        TextView labelView = new TextView(context, null, 0, R.style.PropertiesLabel);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        labelView.setText(label);

        TextView valueView = new TextView(context, null, 0, R.style.PropertiesValue);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        valueView.setTextIsSelectable(true);
        valueView.setText(value);

        row.addView(labelView);
        row.addView(valueView);
        parent.addView(row);
    }

    /** Multi-line message body (e.g. checksum result) in the app font. */
    private static TextView messageView(Context context, String text) {
        TextView view = new TextView(context);
        int side = dp(context, 24);
        view.setPadding(side, dp(context, 16), side, 0);
        view.setTypeface(ResourcesCompat.getFont(context, R.font.font_family));
        view.setTextIsSelectable(true);
        view.setText(text);
        return view;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }
}

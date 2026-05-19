package com.vpt.filemanager.ui.properties;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.system.Os;
import android.system.StructStat;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Future;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.core.concurrent.AppExecutors;
import com.vpt.filemanager.core.io.FolderSizeCalculator;
import com.vpt.filemanager.core.util.ByteSize;
import com.vpt.filemanager.domain.model.FilePath;
import com.vpt.filemanager.domain.model.PosixPermission;
import com.vpt.filemanager.ui.common.BaseDialogFragment;

/**
 * Read-only properties dialog rendered as a 2-column key/value table (MT Manager style — labels
 * left, values right-aligned). Folder size is computed asynchronously via
 * {@link FolderSizeCalculator} so opening Properties on a large directory doesn't block the UI.
 */
@AndroidEntryPoint
public final class PropertiesDialogFragment extends BaseDialogFragment {
    private static final String ARG_PATH = "path";

    @Inject FolderSizeCalculator sizeCalculator;
    @Inject AppExecutors executors;

    private TextView tvSize;
    private Future<Long> sizeFuture;
    private Future<?> sizeWaitFuture;

    public static PropertiesDialogFragment newInstance(String path) {
        PropertiesDialogFragment fragment = new PropertiesDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String rawPath = requireArguments().getString(ARG_PATH, "");
        FilePath path = FilePath.parse(rawPath);
        File file = new File(path.path());

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_properties, null, false);
        populate(content, path, file);

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.properties)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    private void populate(View root, FilePath path, File file) {
        ((TextView) root.findViewById(R.id.tv_name)).setText(
                file.getName().isEmpty() ? path.path() : file.getName());
        ((TextView) root.findViewById(R.id.tv_parent)).setText(
                file.getParent() == null ? "/" : file.getParent());
        ((TextView) root.findViewById(R.id.tv_type)).setText(
                file.isDirectory()
                        ? R.string.properties_type_folder
                        : R.string.properties_type_file);

        tvSize = root.findViewById(R.id.tv_size);
        if (file.isDirectory()) {
            tvSize.setText(R.string.properties_calculating);
            scheduleFolderSize(path);
        } else {
            tvSize.setText(formatSize(file.length()));
        }

        TextView tvModified = root.findViewById(R.id.tv_modified);
        tvModified.setText(file.lastModified() > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(file.lastModified()))
                : getString(R.string.properties_unavailable));

        TextView tvPermissions = root.findViewById(R.id.tv_permissions);
        TextView tvOwner = root.findViewById(R.id.tv_owner);
        TextView tvGroup = root.findViewById(R.id.tv_group);
        try {
            StructStat stat = Os.stat(path.path());
            PosixPermission permission = new PosixPermission(stat.st_mode);
            tvPermissions.setText(String.format(Locale.US, "%s (%s)",
                    permission.toRwxString(),
                    Integer.toOctalString(stat.st_mode & 0777)));
            tvOwner.setText(String.valueOf(stat.st_uid));
            tvGroup.setText(String.valueOf(stat.st_gid));
        } catch (Exception e) {
            String fallback = getString(R.string.properties_unavailable);
            tvPermissions.setText(fallback);
            tvOwner.setText(fallback);
            tvGroup.setText(fallback);
        }
    }

    /**
     * Kick off recursive size computation on a background thread. We submit a separate io() task to
     * await the calculator's Future so we can hop back to the main thread without blocking it; the
     * UI shows "Calculating…" until the result arrives.
     */
    private void scheduleFolderSize(FilePath path) {
        sizeFuture = sizeCalculator.compute(path);
        sizeWaitFuture = executors.io().submit(() -> {
            try {
                long total = sizeFuture.get();
                executors.main().execute(() -> {
                    if (tvSize != null && isAdded()) {
                        tvSize.setText(formatSize(total));
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                // Dialog dismissed or calculator failed — UI keeps "Calculating…" which is fine.
            }
        });
    }

    private static String formatSize(long bytes) {
        return ByteSize.format(bytes) + " (" + bytes + ")";
    }

    /**
     * Force the dialog window to a fixed share of screen width so long Parent paths can't push the
     * 2-column table into wrap_content mode and collapse the value column. AlertDialog's default
     * wrap behavior produced inconsistent widths between root files and deeply-nested files.
     */
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = (int) (metrics.widthPixels * 0.92f);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (sizeFuture != null) sizeFuture.cancel(true);
        if (sizeWaitFuture != null) sizeWaitFuture.cancel(true);
    }
}

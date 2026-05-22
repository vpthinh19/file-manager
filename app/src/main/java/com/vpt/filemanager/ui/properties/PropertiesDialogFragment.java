package com.vpt.filemanager.ui.properties;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.Future;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import com.vpt.filemanager.R;
import com.vpt.filemanager.threading.AppExecutors;
import com.vpt.filemanager.format.ByteSize;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.operations.properties.AndroidPropertiesMetadataReader;
import com.vpt.filemanager.operations.properties.FolderSizeCalculator;
import com.vpt.filemanager.operations.properties.PreparePropertiesModelOperation;
import com.vpt.filemanager.operations.properties.PropertiesMetadataReader;
import com.vpt.filemanager.operations.properties.PropertiesModel;

/**
 * Read-only properties dialog rendered as a 2-column key/value table (MT Manager style — labels
 * left, values right-aligned). Folder size is computed asynchronously via
 * {@link FolderSizeCalculator} so opening Properties on a large directory doesn't block the UI.
 */
@AndroidEntryPoint
public final class PropertiesDialogFragment extends DialogFragment {
    private static final String ARG_PATH = "path";

    @Inject FolderSizeCalculator sizeCalculator;
    @Inject AppExecutors executors;
    @Inject NodeFactory nodeFactory;

    private TextView tvSize;
    private Future<Long> sizeFuture;
    private Future<?> sizeWaitFuture;
    private final PreparePropertiesModelOperation preparePropertiesModelOperation =
            new PreparePropertiesModelOperation();
    private final PropertiesMetadataReader metadataReader =
            new AndroidPropertiesMetadataReader();

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
        NodePath path = NodePath.parse(rawPath);

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_properties, null, false);
        try {
            VirtualNode node = nodeFactory.fromPath(path);
            PropertiesModel model = preparePropertiesModelOperation.execute(
                    new PreparePropertiesModelOperation.Input(node, metadataReader));
            populate(content, model);
        } catch (NodeException e) {
            return new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.properties)
                    .setMessage(e.getMessage() == null
                            ? getString(R.string.properties_unavailable)
                            : e.getMessage())
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.properties)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    private void populate(View root, PropertiesModel model) {
        ((TextView) root.findViewById(R.id.tv_name)).setText(model.name);
        ((TextView) root.findViewById(R.id.tv_parent)).setText(model.parent);
        ((TextView) root.findViewById(R.id.tv_type)).setText(
                model.folder
                        ? R.string.properties_type_folder
                        : R.string.properties_type_file);

        tvSize = root.findViewById(R.id.tv_size);
        if (model.folder) {
            tvSize.setText(R.string.properties_calculating);
            scheduleFolderSize(model.path);
        } else {
            tvSize.setText(formatSize(Math.max(0L, model.sizeBytes)));
        }

        TextView tvModified = root.findViewById(R.id.tv_modified);
        tvModified.setText(model.modifiedAtMillis > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(model.modifiedAtMillis))
                : getString(R.string.properties_unavailable));

        TextView tvPermissions = root.findViewById(R.id.tv_permissions);
        TextView tvOwner = root.findViewById(R.id.tv_owner);
        TextView tvGroup = root.findViewById(R.id.tv_group);
        if (model.posixMetadata == null) {
            String fallback = getString(R.string.properties_unavailable);
            tvPermissions.setText(fallback);
            tvOwner.setText(fallback);
            tvGroup.setText(fallback);
        } else {
            tvPermissions.setText(model.posixMetadata.permissions);
            tvOwner.setText(model.posixMetadata.owner);
            tvGroup.setText(model.posixMetadata.group);
        }
    }

    /**
     * Kick off recursive size computation on a background thread. We submit a separate io() task to
     * await the calculator's Future so we can hop back to the main thread without blocking it; the
     * UI shows "Calculating…" until the result arrives.
     */
    private void scheduleFolderSize(NodePath path) {
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

package com.vpt.filemanager.browser.ui.properties;

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
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.action.properties.PropertiesModel;
import com.vpt.filemanager.browser.action.properties.PropertiesService;
import com.vpt.filemanager.browser.ui.format.ByteSize;
import com.vpt.filemanager.core.threading.AppExecutors;

@AndroidEntryPoint
public final class PropertiesDialogFragment extends DialogFragment {
    private static final String ARG_PATH = "path";

    @Inject PropertiesService properties;
    @Inject AppExecutors executors;
    private TextView sizeView;
    private Future<Long> sizeFuture;
    private Future<?> waitFuture;

    public static PropertiesDialogFragment newInstance(String path) {
        PropertiesDialogFragment fragment = new PropertiesDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_PATH, path);
        fragment.setArguments(arguments);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle state) {
        String path = requireArguments().getString(ARG_PATH, "");
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_properties, null, false);
        try {
            populate(content, properties.read(path));
        } catch (FileOperationException error) {
            return new AlertDialog.Builder(requireContext()).setTitle(R.string.properties)
                    .setMessage(error.getMessage()).setPositiveButton(android.R.string.ok, null)
                    .create();
        }
        return new AlertDialog.Builder(requireContext()).setTitle(R.string.properties)
                .setView(content).setPositiveButton(android.R.string.ok, null).create();
    }

    private void populate(View root, PropertiesModel model) {
        ((TextView) root.findViewById(R.id.tv_name)).setText(model.name);
        ((TextView) root.findViewById(R.id.tv_parent)).setText(model.parent);
        ((TextView) root.findViewById(R.id.tv_type)).setText(model.folder
                ? R.string.properties_type_folder : R.string.properties_type_file);
        sizeView = root.findViewById(R.id.tv_size);
        if (model.folder) {
            sizeView.setText(R.string.properties_calculating);
            sizeFuture = properties.calculateFolderSize(model.path);
            waitFuture = executors.io().submit(() -> {
                try {
                    long size = sizeFuture.get();
                    executors.main().execute(() -> {
                        if (sizeView != null && isAdded()) {
                            sizeView.setText(formatSize(size));
                        }
                    });
                } catch (Exception ignored) {
                }
            });
        } else {
            sizeView.setText(formatSize(Math.max(0L, model.sizeBytes)));
        }
        ((TextView) root.findViewById(R.id.tv_modified)).setText(model.modifiedAtMillis > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(model.modifiedAtMillis))
                : getString(R.string.properties_unavailable));
        String unavailable = getString(R.string.properties_unavailable);
        ((TextView) root.findViewById(R.id.tv_permissions)).setText(model.posixMetadata == null
                ? unavailable : model.posixMetadata.permissions());
        ((TextView) root.findViewById(R.id.tv_owner)).setText(model.posixMetadata == null
                ? unavailable : model.posixMetadata.owner());
        ((TextView) root.findViewById(R.id.tv_group)).setText(model.posixMetadata == null
                ? unavailable : model.posixMetadata.group());
    }

    private static String formatSize(long bytes) {
        return ByteSize.format(bytes) + " (" + bytes + ")";
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            window.setLayout((int) (metrics.widthPixels * 0.92f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (sizeFuture != null) sizeFuture.cancel(true);
        if (waitFuture != null) waitFuture.cancel(true);
        super.onDismiss(dialog);
    }
}

package com.vpt.filemanager.ui.permission;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.vpt.filemanager.R;
import com.vpt.filemanager.ui.common.BaseDialogFragment;

public final class PermissionRationaleDialog extends BaseDialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_body)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}


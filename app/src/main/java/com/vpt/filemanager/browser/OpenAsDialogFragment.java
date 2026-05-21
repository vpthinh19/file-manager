package com.vpt.filemanager.browser;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.vpt.filemanager.R;

/**
 * Dialog shown when the user activates a file whose extension is missing or unrecognised. Offers
 * five viewer choices so the user can disambiguate; the host then dispatches to the matching
 * activity / intent.
 *
 * <p>The choice list mirrors the viewer activities the app already ships (text + system-handled
 * image/video/audio/archive). Keep this enum in sync if a new viewer is added.
 */
public final class OpenAsDialogFragment extends DialogFragment {
    public enum OpenAs {
        TEXT(R.string.openas_text),
        IMAGE(R.string.openas_image),
        VIDEO(R.string.openas_video),
        AUDIO(R.string.openas_audio),
        ARCHIVE(R.string.openas_archive);

        @StringRes public final int labelRes;

        OpenAs(@StringRes int labelRes) {
            this.labelRes = labelRes;
        }
    }

    public interface Listener {
        void onOpenAs(OpenAs choice);
    }

    private static final String ARG_FILENAME = "filename";

    private Listener listener;

    public static OpenAsDialogFragment newInstance(String fileName) {
        OpenAsDialogFragment frag = new OpenAsDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FILENAME, fileName);
        frag.setArguments(args);
        return frag;
    }

    public OpenAsDialogFragment setListener(Listener listener) {
        this.listener = listener;
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String fileName = getArguments() == null ? "" : getArguments().getString(ARG_FILENAME, "");
        OpenAs[] options = OpenAs.values();
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = getString(options[i].labelRes);
        }
        return new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.openas_title, fileName))
                .setItems(labels, (dialog, which) -> {
                    if (listener != null) {
                        listener.onOpenAs(options[which]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}

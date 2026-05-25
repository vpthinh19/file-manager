package com.vpt.filemanager.ui.content;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.vpt.filemanager.R;
import com.vpt.filemanager.state.StateViewModel;

import java.io.File;

public final class ImageContentFragment extends Fragment implements FullScreenContent {
    private static final String ARG_PATH = "path";
    private ImageView image;
    private RequestManager requests;

    public static ImageContentFragment newInstance(String path) {
        ImageContentFragment fragment = new ImageContentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                                       @Nullable Bundle state) {
        return inflater.inflate(R.layout.content_image_viewer, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        String path = requireArguments().getString(ARG_PATH);
        MaterialToolbar toolbar = view.findViewById(R.id.image_toolbar);
        toolbar.setTitle(new File(path).getName());
        toolbar.setNavigationOnClickListener(ignored -> onBackPressed());
        image = view.findViewById(R.id.image_content);
        requests = Glide.with(this);
        requests.load(new File(path)).fitCenter().into(image);
    }

    @Override public boolean onBackPressed() {
        new ViewModelProvider(requireActivity()).get(StateViewModel.class)
                .back(new ViewModelProvider(requireActivity()).get(StateViewModel.class).activePaneValue());
        return true;
    }

    @Override public void onDestroyView() {
        if (requests != null && image != null) requests.clear(image);
        image = null;
        requests = null;
        super.onDestroyView();
    }
}

package com.vpt.filemanager.component.content;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.vpt.filemanager.R;
import com.vpt.filemanager.state.StateViewModel;

import java.io.File;

public final class MediaContentFragment extends Fragment implements FullScreenContent {
    private static final String ARG_PATH = "path";
    private static final String ARG_NAME = "name";
    private static final String ARG_VIDEO = "video";
    private PlayerView playerView;
    private ExoPlayer player;
    private long position;
    private boolean playing = true;

    public static MediaContentFragment newInstance(String path, String name, boolean video) {
        MediaContentFragment fragment = new MediaContentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, path);
        args.putString(ARG_NAME, name);
        args.putBoolean(ARG_VIDEO, video);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                                       @Nullable Bundle state) {
        if (state != null) {
            position = state.getLong("position");
            playing = state.getBoolean("playing", true);
        }
        return inflater.inflate(R.layout.content_media_player, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        boolean video = requireArguments().getBoolean(ARG_VIDEO);
        MaterialToolbar toolbar = view.findViewById(R.id.media_toolbar);
        toolbar.setTitle(requireArguments().getString(ARG_NAME));
        toolbar.setSubtitle(video ? R.string.media_video : R.string.media_audio);
        toolbar.setNavigationOnClickListener(ignored -> onBackPressed());
        playerView = view.findViewById(R.id.player_view);
        playerView.setUseArtwork(!video);
    }

    @Override public void onStart() {
        super.onStart();
        player = new ExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(
                Uri.fromFile(new File(requireArguments().getString(ARG_PATH)))));
        player.seekTo(position);
        player.setPlayWhenReady(playing);
        player.prepare();
    }

    @Override public void onStop() {
        if (player != null) {
            position = player.getCurrentPosition();
            playing = player.getPlayWhenReady();
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        super.onStop();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle output) {
        output.putLong("position", player == null ? position : player.getCurrentPosition());
        output.putBoolean("playing", player == null ? playing : player.getPlayWhenReady());
        super.onSaveInstanceState(output);
    }

    @Override public boolean onBackPressed() {
        StateViewModel state = new ViewModelProvider(requireActivity()).get(StateViewModel.class);
        state.back(state.activePaneValue());
        return true;
    }
}

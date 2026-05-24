package com.vpt.filemanager.media.playback;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.vpt.filemanager.R;

import java.io.File;

/** Local audio/video playback surface. Player allocation follows the visible activity lifecycle. */
public final class MediaPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "com.vpt.filemanager.extra.MEDIA_PATH";
    public static final String EXTRA_VIDEO = "com.vpt.filemanager.extra.IS_VIDEO";

    private String path;
    private boolean video;
    private PlayerView playerView;
    private ExoPlayer player;
    private long resumePosition;
    private boolean resumePlay = true;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        path = getIntent().getStringExtra(EXTRA_PATH);
        video = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        if (path == null) {
            finish();
            return;
        }
        if (state != null) {
            resumePosition = state.getLong("position");
            resumePlay = state.getBoolean("play", true);
        }
        setContentView(R.layout.activity_media_player);
        applyInsets();
        MaterialToolbar toolbar = findViewById(R.id.media_toolbar);
        toolbar.setTitle(new File(path).getName());
        toolbar.setSubtitle(video ? R.string.media_video : R.string.media_audio);
        toolbar.setNavigationOnClickListener(view -> finish());
        playerView = findViewById(R.id.player_view);
        playerView.setUseArtwork(!video);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (path == null) {
            return;
        }
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(new File(path))));
        player.seekTo(resumePosition);
        player.setPlayWhenReady(resumePlay);
        player.prepare();
    }

    @Override
    protected void onStop() {
        releasePlayer();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (player != null) {
            state.putLong("position", player.getCurrentPosition());
            state.putBoolean("play", player.getPlayWhenReady());
        } else {
            state.putLong("position", resumePosition);
            state.putBoolean("play", resumePlay);
        }
        super.onSaveInstanceState(state);
    }

    private void releasePlayer() {
        if (player == null) {
            return;
        }
        resumePosition = player.getCurrentPosition();
        resumePlay = player.getPlayWhenReady();
        playerView.setPlayer(null);
        player.release();
        player = null;
    }

    private void applyInsets() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        View root = findViewById(R.id.media_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
            return insets;
        });
    }
}

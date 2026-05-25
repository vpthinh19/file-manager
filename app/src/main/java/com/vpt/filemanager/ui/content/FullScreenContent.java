package com.vpt.filemanager.ui.content;

/** Allows a full-screen component to consume Back, e.g. to confirm unsaved editor changes. */
public interface FullScreenContent {
    boolean onBackPressed();
}

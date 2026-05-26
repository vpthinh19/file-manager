package com.vpt.filemanager.component.content;

/** Allows a full-screen component to consume Back, e.g. to confirm unsaved editor changes. */
public interface FullScreenContent {
    boolean onBackPressed();
}

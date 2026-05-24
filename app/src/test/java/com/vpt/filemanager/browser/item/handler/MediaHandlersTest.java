package com.vpt.filemanager.browser.item.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.item.ItemType;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.PaneId;

public final class MediaHandlersTest {
    @Test
    public void imageProducesDedicatedViewerEffect() throws Exception {
        Item image = Item.local("/storage/a.jpg", "a.jpg", false, 1, 0, ItemType.IMAGE);
        ActionResult result = new ImageHandler(null).activate(PaneId.LEFT, image);

        WorkspaceEffect.OpenImage effect =
                (WorkspaceEffect.OpenImage) ((ActionResult.Effect) result).effect();
        assertEquals(image.localPath(), effect.path());
    }

    @Test
    public void videoAndAudioChoosePlaybackSurfaceMode() throws Exception {
        Item video = Item.local("/storage/a.mp4", "a.mp4", false, 1, 0, ItemType.VIDEO);
        Item audio = Item.local("/storage/a.mp3", "a.mp3", false, 1, 0, ItemType.AUDIO);

        WorkspaceEffect.OpenMedia videoEffect = (WorkspaceEffect.OpenMedia)
                ((ActionResult.Effect) new VideoHandler(null).activate(PaneId.LEFT, video)).effect();
        WorkspaceEffect.OpenMedia audioEffect = (WorkspaceEffect.OpenMedia)
                ((ActionResult.Effect) new AudioHandler(null).activate(PaneId.LEFT, audio)).effect();

        assertTrue(videoEffect.video());
        assertFalse(audioEffect.video());
    }
}

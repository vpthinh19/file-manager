package com.vpt.filemanager.browser.item.handler;

import java.util.EnumMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.vpt.filemanager.browser.action.ActionResult;
import com.vpt.filemanager.browser.item.ItemType;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.workspace.effect.WorkspaceEffect;
import com.vpt.filemanager.browser.workspace.state.PaneId;
import com.vpt.filemanager.core.error.FileOperationException;

@Singleton
public final class ItemHandlerRegistry {
    private final EnumMap<ItemType, ItemHandler> handlers = new EnumMap<>(ItemType.class);

    @Inject
    public ItemHandlerRegistry(ParentHandler parent, FolderHandler folder,
                               TextHandler text, ImageHandler image, VideoHandler video,
                               AudioHandler audio, ExternalHandler external,
                               ArchiveHandler archive) {
        handlers.put(ItemType.PARENT, parent);
        handlers.put(ItemType.FOLDER, folder);
        handlers.put(ItemType.TEXT, text);
        handlers.put(ItemType.IMAGE, image);
        handlers.put(ItemType.VIDEO, video);
        handlers.put(ItemType.AUDIO, audio);
        handlers.put(ItemType.EXTERNAL, external);
        handlers.put(ItemType.ARCHIVE, archive);
    }

    public ActionResult activate(PaneId pane, Item item) throws FileOperationException {
        ItemHandler handler = handlers.get(item.behavior());
        return handler == null
                ? new ActionResult.Effect(new WorkspaceEffect.Toast("Item cannot be opened"))
                : handler.activate(pane, item);
    }
}

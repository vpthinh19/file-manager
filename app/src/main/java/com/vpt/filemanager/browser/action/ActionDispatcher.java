package com.vpt.filemanager.browser.action;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import com.vpt.filemanager.browser.action.bookmark.AddBookmarkAction;
import com.vpt.filemanager.browser.action.bookmark.AddBookmarkActionHandler;
import com.vpt.filemanager.browser.action.bookmark.RemoveBookmarksAction;
import com.vpt.filemanager.browser.action.bookmark.RemoveBookmarksActionHandler;
import com.vpt.filemanager.browser.action.browse.ActivateItemAction;
import com.vpt.filemanager.browser.action.browse.ActivateItemActionHandler;
import com.vpt.filemanager.browser.action.browse.ActivatePaneAction;
import com.vpt.filemanager.browser.action.browse.ActivatePaneActionHandler;
import com.vpt.filemanager.browser.action.browse.BackAction;
import com.vpt.filemanager.browser.action.browse.BackActionHandler;
import com.vpt.filemanager.browser.action.browse.ChangeSortAction;
import com.vpt.filemanager.browser.action.browse.ChangeSortActionHandler;
import com.vpt.filemanager.browser.action.browse.ForwardAction;
import com.vpt.filemanager.browser.action.browse.ForwardActionHandler;
import com.vpt.filemanager.browser.action.browse.NavigateAction;
import com.vpt.filemanager.browser.action.browse.NavigateActionHandler;
import com.vpt.filemanager.browser.action.browse.RefreshAction;
import com.vpt.filemanager.browser.action.browse.RefreshActionHandler;
import com.vpt.filemanager.browser.action.browse.SearchAction;
import com.vpt.filemanager.browser.action.browse.SearchActionHandler;
import com.vpt.filemanager.browser.action.browse.SwitchActivePaneAction;
import com.vpt.filemanager.browser.action.browse.SwitchActivePaneHandler;
import com.vpt.filemanager.browser.action.browse.UpAction;
import com.vpt.filemanager.browser.action.browse.UpActionHandler;
import com.vpt.filemanager.browser.action.entry.CompressAction;
import com.vpt.filemanager.browser.action.entry.CompressActionHandler;
import com.vpt.filemanager.browser.action.entry.CreateEntryAction;
import com.vpt.filemanager.browser.action.entry.CreateEntryActionHandler;
import com.vpt.filemanager.browser.action.entry.DeleteEntriesAction;
import com.vpt.filemanager.browser.action.entry.DeleteEntriesActionHandler;
import com.vpt.filemanager.browser.action.entry.RenameEntryAction;
import com.vpt.filemanager.browser.action.entry.RenameEntryActionHandler;
import com.vpt.filemanager.browser.action.open.OpenWithAction;
import com.vpt.filemanager.browser.action.open.OpenWithActionHandler;
import com.vpt.filemanager.browser.action.properties.PropertiesAction;
import com.vpt.filemanager.browser.action.properties.PropertiesActionHandler;
import com.vpt.filemanager.browser.action.open.ToolsAction;
import com.vpt.filemanager.browser.action.open.ToolsActionHandler;
import com.vpt.filemanager.browser.action.selection.ClearSelectionAction;
import com.vpt.filemanager.browser.action.selection.ClearSelectionActionHandler;
import com.vpt.filemanager.browser.action.selection.SelectAllAction;
import com.vpt.filemanager.browser.action.selection.SelectAllActionHandler;
import com.vpt.filemanager.browser.action.selection.SelectRangeAction;
import com.vpt.filemanager.browser.action.selection.SelectRangeActionHandler;
import com.vpt.filemanager.browser.action.selection.ToggleSelectionAction;
import com.vpt.filemanager.browser.action.selection.ToggleSelectionActionHandler;
import com.vpt.filemanager.browser.action.share.ShareAction;
import com.vpt.filemanager.browser.action.share.ShareActionHandler;
import com.vpt.filemanager.browser.action.transfer.TransferEntriesAction;
import com.vpt.filemanager.browser.action.transfer.TransferEntriesHandler;
import com.vpt.filemanager.browser.action.trash.EmptyTrashAction;
import com.vpt.filemanager.browser.action.trash.EmptyTrashActionHandler;
import com.vpt.filemanager.browser.action.trash.RestoreTrashAction;
import com.vpt.filemanager.browser.action.trash.RestoreTrashActionHandler;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.browser.workspace.state.WorkspaceSnapshot;

/** Typed routing table; the unchecked cast is isolated to this registration boundary. */
public final class ActionDispatcher {
    private final Map<Class<? extends Action>, ActionHandler<?>> handlers = new HashMap<>();

    @Inject
    public ActionDispatcher(ActivatePaneActionHandler activatePane, ActivateItemActionHandler activateItem,
                            SwitchActivePaneHandler switchPane, NavigateActionHandler navigate,
                            BackActionHandler back, ForwardActionHandler forward, UpActionHandler up,
                            RefreshActionHandler refresh, SearchActionHandler search, ChangeSortActionHandler sort,
                            ToggleSelectionActionHandler toggle, SelectAllActionHandler selectAll,
                            SelectRangeActionHandler selectRange, ClearSelectionActionHandler clear,
                            CreateEntryActionHandler create, RenameEntryActionHandler rename,
                            DeleteEntriesActionHandler delete, CompressActionHandler compress,
                            TransferEntriesHandler transfer, AddBookmarkActionHandler bookmark,
                            RemoveBookmarksActionHandler removeBookmarks, RestoreTrashActionHandler restore,
                            EmptyTrashActionHandler emptyTrash, PropertiesActionHandler properties,
                            OpenWithActionHandler openWith, ToolsActionHandler tools, ShareActionHandler share) {
        register(ActivatePaneAction.class, activatePane);
        register(ActivateItemAction.class, activateItem);
        register(SwitchActivePaneAction.class, switchPane);
        register(NavigateAction.class, navigate);
        register(BackAction.class, back);
        register(ForwardAction.class, forward);
        register(UpAction.class, up);
        register(RefreshAction.class, refresh);
        register(SearchAction.class, search);
        register(ChangeSortAction.class, sort);
        register(ToggleSelectionAction.class, toggle);
        register(SelectAllAction.class, selectAll);
        register(SelectRangeAction.class, selectRange);
        register(ClearSelectionAction.class, clear);
        register(CreateEntryAction.class, create);
        register(RenameEntryAction.class, rename);
        register(DeleteEntriesAction.class, delete);
        register(CompressAction.class, compress);
        register(TransferEntriesAction.class, transfer);
        register(AddBookmarkAction.class, bookmark);
        register(RemoveBookmarksAction.class, removeBookmarks);
        register(RestoreTrashAction.class, restore);
        register(EmptyTrashAction.class, emptyTrash);
        register(PropertiesAction.class, properties);
        register(OpenWithAction.class, openWith);
        register(ToolsAction.class, tools);
        register(ShareAction.class, share);
    }

    private <A extends Action> void register(Class<A> type, ActionHandler<A> handler) {
        handlers.put(type, handler);
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public <A extends Action> ActionResult dispatch(@NonNull A action,
                                                     @NonNull WorkspaceSnapshot state)
            throws FileOperationException {
        ActionHandler<A> handler = (ActionHandler<A>) handlers.get(action.getClass());
        if (handler == null) throw new FileOperationException("No handler for " + action.getClass().getSimpleName());
        return handler.handle(action, state);
    }
}

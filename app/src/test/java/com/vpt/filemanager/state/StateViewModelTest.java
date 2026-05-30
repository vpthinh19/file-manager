package com.vpt.filemanager.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.test.core.app.ApplicationProvider;

import com.vpt.filemanager.core.format.ContentType;
import com.vpt.filemanager.core.path.Path;
import com.vpt.filemanager.app.settings.UserPreferences;
import com.vpt.filemanager.component.content.OpenedContent;
import com.vpt.filemanager.component.pane.PaneId;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class StateViewModelTest {
    private StateViewModel state;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("file_manager_preferences", Context.MODE_PRIVATE).edit().clear().commit();
        state = new StateViewModel(new UserPreferences(context));
    }

    @Test
    public void contentBackRestoresActualPreviousCollection() {
        state.navigate(PaneId.LEFT, Path.bookmarks());
        Path file = Path.storage("/Documents/report.txt");
        state.navigate(PaneId.LEFT, file);
        state.showContent(new OpenedContent(PaneId.LEFT, file, "/tmp/report.txt", "content://report",
                "report.txt",
                ContentType.TEXT, false, null));
        assertTrue(state.back(PaneId.LEFT));
        assertEquals(Path.bookmarks(), state.current(PaneId.LEFT).location);
        assertNull(state.contentValue());
    }

    @Test
    public void paneActivationAndHistoryStayIndependent() {
        Path leftFolder = Path.storage("/Download");
        state.navigate(PaneId.LEFT, leftFolder);
        state.activate(PaneId.RIGHT);
        assertEquals(Path.storageRoot(), state.current(PaneId.RIGHT).location);
        assertTrue(state.current(PaneId.LEFT).canGoBack);
        assertFalse(state.current(PaneId.RIGHT).canGoBack);
    }

    @Test
    public void scrollPositionIsRetainedPerLocation() {
        Parcelable position = new Bundle();
        state.saveScroll(PaneId.LEFT, Path.storageRoot(), position);
        state.navigate(PaneId.LEFT, Path.storage("/Download"));

        assertSame(position, state.savedScroll(PaneId.LEFT, Path.storageRoot()));
        assertNull(state.savedScroll(PaneId.LEFT, Path.storage("/Download")));
    }
}

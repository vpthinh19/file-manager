package com.vpt.filemanager.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.vpt.filemanager.model.ContentKind;
import com.vpt.filemanager.model.Location;
import com.vpt.filemanager.storage.persistence.UserPreferences;

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
        state.navigate(PaneId.LEFT, Location.bookmarks());
        Location file = Location.storage("/storage/emulated/0/Documents/report.txt");
        state.navigate(PaneId.LEFT, file);
        state.showContent(new ContentState(PaneId.LEFT, file, "/tmp/report.txt", "report.txt",
                ContentKind.TEXT, false, null));
        assertTrue(state.back(PaneId.LEFT));
        assertEquals(Location.bookmarks(), state.current(PaneId.LEFT).location);
        assertNull(state.contentValue());
    }

    @Test
    public void paneActivationAndHistoryStayIndependent() {
        Location leftFolder = Location.storage("/storage/emulated/0/Download");
        state.navigate(PaneId.LEFT, leftFolder);
        state.activate(PaneId.RIGHT);
        assertEquals(Location.storageRoot(), state.current(PaneId.RIGHT).location);
        assertTrue(state.current(PaneId.LEFT).canGoBack);
        assertFalse(state.current(PaneId.RIGHT).canGoBack);
    }
}

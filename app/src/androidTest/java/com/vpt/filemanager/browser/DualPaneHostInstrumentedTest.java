package com.vpt.filemanager.browser;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

import com.vpt.filemanager.R;
import com.vpt.filemanager.ui.main.MainActivity;

@RunWith(AndroidJUnit4.class)
public final class DualPaneHostInstrumentedTest {
    @BeforeClass
    public static void grantAllFilesAccess() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String command = "appops set " + context.getPackageName()
                + " MANAGE_EXTERNAL_STORAGE allow";
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
        try (InputStream result = new ParcelFileDescriptor.AutoCloseInputStream(output)) {
            while (result.read() != -1) {
                // Drain output so appops completes before the activity checks permission.
            }
        }
    }

    @Test
    public void launch_rendersBothPanesAndCommandBar() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.pane_left_container)).check(matches(isDisplayed()));
            onView(withId(R.id.pane_right_container)).check(matches(isDisplayed()));
            onView(withId(R.id.btn_add)).check(matches(isDisplayed()));
        }
    }
}

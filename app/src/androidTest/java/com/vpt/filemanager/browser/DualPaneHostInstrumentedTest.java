package com.vpt.filemanager.browser;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matcher;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
            try {
                waitForView(allOf(withText("Download"),
                        isDescendantOfA(withId(R.id.pane_left_container))));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            onView(allOf(withText(".."), isDescendantOfA(withId(R.id.pane_left_container))))
                    .check(doesNotExist());
            onView(withId(R.id.btn_up)).check(matches(not(isEnabled())));
        }
    }

    @Test
    public void search_rendersVirtualResultsAndRefreshesAfterExternalChange() throws Exception {
        Path scope = Paths.get("/storage/emulated/0/CodexSearchScope");
        Path match = scope.resolve("codex-search-needle.txt");
        Files.createDirectories(scope);
        Files.write(match, "needle".getBytes(StandardCharsets.UTF_8));

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            Matcher<View> leftScope = allOf(withText("CodexSearchScope"),
                    isDescendantOfA(withId(R.id.pane_left_container)));
            waitForView(leftScope);
            onView(leftScope).perform(click());

            onView(allOf(withContentDescription(R.string.menu_more_options),
                    isDescendantOfA(withId(R.id.toolbar)))).perform(click());
            onView(withText(R.string.action_search)).perform(click());
            onView(withId(R.id.et_name)).perform(replaceText("codex-search-needle"));
            onView(withId(android.R.id.button1)).perform(click());

            Matcher<View> result = allOf(withText("codex-search-needle.txt"),
                    isDescendantOfA(withId(R.id.pane_left_container)));
            waitForView(result);
            onView(withText("Search: codex-search-needle")).check(matches(isDisplayed()));

            Files.delete(match);
            waitForNoView(result);
        } finally {
            Files.deleteIfExists(match);
            Files.deleteIfExists(scope);
        }
    }

    @Test
    public void archivePane_createFile_rewritesPhysicalZipContainer() throws Exception {
        Path scope = Paths.get("/storage/emulated/0/CodexArchiveScope");
        Path archive = scope.resolve("editable.zip");
        Files.createDirectories(scope);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("seed.txt"));
            output.write("seed".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            Matcher<View> scopeRow = allOf(withText("CodexArchiveScope"),
                    isDescendantOfA(withId(R.id.pane_left_container)));
            waitForView(scopeRow);
            onView(scopeRow).perform(click());

            Matcher<View> archiveRow = allOf(withText("editable.zip"),
                    isDescendantOfA(withId(R.id.pane_left_container)));
            waitForView(archiveRow);
            onView(archiveRow).perform(click());
            waitForView(allOf(withText("seed.txt"),
                    isDescendantOfA(withId(R.id.pane_left_container))));

            onView(withId(R.id.btn_add)).check(matches(isEnabled())).perform(click());
            onView(withId(R.id.btn_type_file)).perform(click());
            onView(withId(R.id.et_name)).perform(replaceText("created.txt"));
            onView(withId(android.R.id.button1)).perform(click());

            waitForView(allOf(withText("created.txt"),
                    isDescendantOfA(withId(R.id.pane_left_container))));
        } finally {
            try (ZipFile result = new ZipFile(archive.toFile())) {
                org.junit.Assert.assertTrue(result.getEntry("seed.txt") != null);
                org.junit.Assert.assertTrue(result.getEntry("created.txt") != null);
            }
            Files.deleteIfExists(archive);
            Files.deleteIfExists(scope);
        }
    }

    private static void waitForView(Matcher<View> matcher) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (NoMatchingViewException | AssertionError error) {
                Thread.sleep(100);
            }
        }
        onView(matcher).check(matches(isDisplayed()));
    }

    private static void waitForNoView(Matcher<View> matcher) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(matcher).check(doesNotExist());
                return;
            } catch (AssertionError error) {
                Thread.sleep(100);
            }
        }
        onView(matcher).check(doesNotExist());
    }
}

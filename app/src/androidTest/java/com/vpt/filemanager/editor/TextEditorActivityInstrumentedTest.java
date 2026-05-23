package com.vpt.filemanager.editor;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.vpt.filemanager.ui.editor.TextEditorActivity;
import com.vpt.filemanager.R;
import com.vpt.filemanager.node.NodePath;

@RunWith(AndroidJUnit4.class)
public final class TextEditorActivityInstrumentedTest {
    @Test
    public void opensLocalJavaFileInEditor() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Path file = context.getCacheDir().toPath().resolve("SyntaxSmoke.java");
        Files.write(file, "public class SyntaxSmoke {}\n".getBytes(StandardCharsets.UTF_8));

        Intent intent = new Intent(context, TextEditorActivity.class);
        intent.putExtra(TextEditorActivity.EXTRA_PATH,
                NodePath.local(file.toString()).toString());

        try (ActivityScenario<TextEditorActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withText("SyntaxSmoke.java")).check(matches(isDisplayed()));
            onView(withContentDescription("Save")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void searchCommand_findsMatchesInDocument() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Path file = context.getCacheDir().toPath().resolve("FindSmoke.txt");
        Files.write(file, "alpha beta alpha\n".getBytes(StandardCharsets.UTF_8));

        Intent intent = new Intent(context, TextEditorActivity.class);
        intent.putExtra(TextEditorActivity.EXTRA_PATH,
                NodePath.local(file.toString()).toString());

        try (ActivityScenario<TextEditorActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withContentDescription("Search")).perform(click());
            onView(withId(R.id.editor_search_input)).perform(replaceText("alpha"));
            waitForView(withText("1 / 2"));
        }
    }

    @Test
    public void externalDelete_reportsMissingDocument() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Path file = context.getCacheDir().toPath().resolve("DeletedSmoke.txt");
        Files.write(file, "content\n".getBytes(StandardCharsets.UTF_8));

        Intent intent = new Intent(context, TextEditorActivity.class);
        intent.putExtra(TextEditorActivity.EXTRA_PATH,
                NodePath.local(file.toString()).toString());

        try (ActivityScenario<TextEditorActivity> ignored = ActivityScenario.launch(intent)) {
            waitForView(withText("UTF-8"));
            Files.delete(file);
            waitForView(withText(R.string.editor_external_title));
            onView(withText(R.string.editor_deleted_external_message)).check(matches(isDisplayed()));
        }
    }

    private static void waitForView(Matcher<View> matcher) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        AssertionError lastAssertion = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (NoMatchingViewException | AssertionError error) {
                if (error instanceof AssertionError) {
                    lastAssertion = (AssertionError) error;
                }
                Thread.sleep(100);
            }
        }
        if (lastAssertion != null) {
            throw lastAssertion;
        }
        onView(matcher).check(matches(isDisplayed()));
    }
}

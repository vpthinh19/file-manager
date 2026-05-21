package com.vpt.filemanager.editor;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(AndroidJUnit4.class)
public final class TextEditorActivityInstrumentedTest {
    @Test
    public void opensLocalJavaFileInEditor() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Path file = context.getCacheDir().toPath().resolve("SyntaxSmoke.java");
        Files.write(file, "public class SyntaxSmoke {}\n".getBytes(StandardCharsets.UTF_8));

        Intent intent = new Intent(context, TextEditorActivity.class);
        intent.putExtra(TextEditorActivity.EXTRA_PATH, file.toString());

        try (ActivityScenario<TextEditorActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withText(file.toString())).check(matches(isDisplayed()));
            onView(withText("Save")).check(matches(isDisplayed()));
        }
    }
}

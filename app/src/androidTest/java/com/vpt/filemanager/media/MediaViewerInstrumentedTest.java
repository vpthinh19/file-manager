package com.vpt.filemanager.media;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vpt.filemanager.R;
import com.vpt.filemanager.media.image.ImageViewerActivity;
import com.vpt.filemanager.media.playback.MediaPlayerActivity;

@RunWith(AndroidJUnit4.class)
public final class MediaViewerInstrumentedTest {
    @Test
    public void glideImageViewerLoadsLocalImage() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File image = new File(context.getCacheDir(), "glide-preview.png");
        Bitmap bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        try (FileOutputStream output = new FileOutputStream(image)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }

        Intent intent = new Intent(context, ImageViewerActivity.class)
                .putExtra(ImageViewerActivity.EXTRA_PATH, image.getAbsolutePath());
        try (ActivityScenario<ImageViewerActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.image_content)).check(matches(isDisplayed()));
            AtomicBoolean drawableLoaded = new AtomicBoolean();
            long deadline = System.currentTimeMillis() + 3000;
            while (!drawableLoaded.get() && System.currentTimeMillis() < deadline) {
                scenario.onActivity(activity -> drawableLoaded.set(
                        ((ImageView) activity.findViewById(R.id.image_content)).getDrawable() != null));
                if (!drawableLoaded.get()) Thread.sleep(50);
            }
            assertTrue(drawableLoaded.get());
        } finally {
            image.delete();
        }
    }

    @Test
    public void media3AudioSurfaceStartsWithLocalWav() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File audio = new File(context.getCacheDir(), "media3-tone.wav");
        writeSilentWav(audio);
        Intent intent = new Intent(context, MediaPlayerActivity.class)
                .putExtra(MediaPlayerActivity.EXTRA_PATH, audio.getAbsolutePath())
                .putExtra(MediaPlayerActivity.EXTRA_VIDEO, false);
        try (ActivityScenario<MediaPlayerActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withId(R.id.player_view)).check(matches(isDisplayed()));
            Thread.sleep(250);
        } finally {
            audio.delete();
        }
    }

    private static void writeSilentWav(File target) throws IOException {
        int sampleRate = 8000;
        int samples = sampleRate / 10;
        int bytes = samples * 2;
        ByteBuffer data = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN);
        data.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + bytes);
        data.put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '}).putInt(16);
        data.putShort((short) 1).putShort((short) 1).putInt(sampleRate);
        data.putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        data.put(new byte[]{'d', 'a', 't', 'a'}).putInt(bytes);
        while (data.remaining() >= 2) data.putShort((short) 0);
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(data.array());
        }
    }
}

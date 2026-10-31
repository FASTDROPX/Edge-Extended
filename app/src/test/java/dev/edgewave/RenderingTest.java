package dev.edgewave;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RenderingTest {
  Bitmap white(Bitmap image) {
    Bitmap out = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(out);
    c.drawColor(Color.WHITE);
    c.drawBitmap(image, 0, 0, null);
    return out;
  }

  Bitmap draw(WaveView view) {
    view.layout(0, 0, 720, 280);
    Bitmap b = Bitmap.createBitmap(720, 280, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(b));
    return b;
  }

  @Test
  public void zeroVolumeFullyDisappears() {
    Context c = RuntimeEnvironment.getApplication();
    WaveView v = new WaveView(c, false);
    v.sounding = true;
    v.muted = true;
    v.appear = 0;
    Bitmap b = draw(v);
    for (int y = 0; y < b.getHeight(); y++)
      for (int x = 0; x < b.getWidth(); x++)
        assertEquals("mute leaves pixel", 0, Color.alpha(b.getPixel(x, y)));
  }

  @Test
  public void demoPauseLeavesNoStrip() {
    WaveView v = new WaveView(RuntimeEnvironment.getApplication(), true);
    v.demoPlaying = false;
    v.appear = 0;
    Bitmap b = draw(v);
    assertEquals(0, Color.alpha(b.getPixel(360, 2)));
    assertEquals(0, Color.alpha(b.getPixel(360, 80)));
  }

  @Test
  public void activeFillIsOpaqueBlack() throws Exception {
    WaveView v = new WaveView(RuntimeEnvironment.getApplication(), true);
    v.appear = 1;
    for (int i = 0; i < 48; i++) v.levels[i] = .8f;
    Bitmap b = draw(v);
    assertEquals(0xff000000, b.getPixel(360, 5));
    assertEquals(0xff000000, b.getPixel(360, 20));
    for (int y = 0; y < b.getHeight(); y++)
      for (int x = 0; x < b.getWidth(); x++) {
        int pixel = b.getPixel(x, y);
        if (Color.alpha(pixel) == 255) assertEquals(0xff000000, pixel);
      }
    int back = 0, middle = 0;
    for (int y = 0; y < b.getHeight(); y++)
      for (int x = 0; x < b.getWidth(); x++) {
        int alpha = Color.alpha(b.getPixel(x, y));
        if (alpha == 55) back++;
        if (alpha >= 150 && alpha <= 154) middle++;
      }
    assertTrue("back layer missing", back > 50);
    assertTrue("middle layer missing", middle > 50);
    File folder = new File("build/render-checks");
    folder.mkdirs();
    try (FileOutputStream f = new FileOutputStream(new File(folder, "wave.png"))) {
      white(b).compress(Bitmap.CompressFormat.PNG, 100, f);
    }
  }

  @Test
  public void dentHasFlatMiddleAndBlackFill() throws Exception {
    Bitmap b = Bitmap.createBitmap(100, 320, Bitmap.Config.ARGB_8888);
    Path p = new Path();
    DentShape.trace(p, 60, 150, 160);
    Paint paint = new Paint(3);
    paint.setColor(Color.BLACK);
    new Canvas(b).drawPath(p, paint);
    assertEquals(Color.BLACK, b.getPixel(58, 140));
    assertEquals(Color.BLACK, b.getPixel(58, 180));
    assertEquals(0, Color.alpha(b.getPixel(70, 160)));
    File folder = new File("build/render-checks");
    folder.mkdirs();
    try (FileOutputStream f = new FileOutputStream(new File(folder, "dent.png"))) {
      white(b).compress(Bitmap.CompressFormat.PNG, 100, f);
    }
  }

  @Test
  public void launcherIconRenders() throws Exception {
    Bitmap b = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888);
    android.graphics.drawable.Drawable icon =
        RuntimeEnvironment.getApplication().getDrawable(dev.edgewave.R.mipmap.ic_launcher);
    icon.setBounds(0, 0, 216, 216);
    icon.draw(new Canvas(b));
    File folder = new File("build/render-checks");
    folder.mkdirs();
    try (FileOutputStream f = new FileOutputStream(new File(folder, "icon.png"))) {
      white(b).compress(Bitmap.CompressFormat.PNG, 100, f);
    }
  }

  @Test
  public void mainScreenRenders() throws Exception {
    ((android.media.AudioManager)
            RuntimeEnvironment.getApplication().getSystemService(Context.AUDIO_SERVICE))
        .setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 5, 0);
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    android.view.View view = activity.getWindow().getDecorView();
    int exact = android.view.View.MeasureSpec.EXACTLY;
    view.measure(
        android.view.View.MeasureSpec.makeMeasureSpec(720, exact),
        android.view.View.MeasureSpec.makeMeasureSpec(1600, exact));
    view.layout(0, 0, 720, 1600);
    Bitmap b = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(b));
    File folder = new File("build/render-checks");
    folder.mkdirs();
    try (FileOutputStream f = new FileOutputStream(new File(folder, "screen.png"))) {
      white(b).compress(Bitmap.CompressFormat.PNG, 100, f);
    }
  }

  @Test
  public void mutingActiveWaveRetractsTheWholeFill() {
    WaveView v = new WaveView(RuntimeEnvironment.getApplication(), false);
    v.layout(0, 0, 720, 280);
    v.appear = 1;
    v.sounding = true;
    v.muted = true;
    Bitmap b = Bitmap.createBitmap(720, 280, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    for (int i = 0; i < 90; i++) {
      b.eraseColor(Color.TRANSPARENT);
      v.last = System.nanoTime() - 16666666;
      v.draw(c);
    }
    for (int y = 0; y < 280; y++)
      for (int x = 0; x < 720; x++) assertEquals(0, Color.alpha(b.getPixel(x, y)));
  }

  @Test
  public void overlayAvoidsAndroidForcedOpacityClamp() {
    android.view.WindowManager.LayoutParams p = OverlayWindows.create(80, 300);
    assertEquals(1f, p.alpha, 0);
    assertEquals(0, p.flags & android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    assertTrue((p.flags & android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0);
  }
}

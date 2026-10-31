package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EffectsRenderTest {
  @Test
  public void allEightEffectsRenderInsideSmallOccupiedWindows() throws Exception {
    File folder = new File("build/effects-checks");
    folder.mkdirs();
    float[] wave = new float[48];
    for (int i = 0; i < 48; i++) wave[i] = 65 + 9 * (float) Math.sin(i * .2);
    for (int kind = 0; kind < EffectsEngine.COUNT; kind++) {
      EffectsEngine.Fx fx =
          new EffectsEngine.Fx(kind + 1, kind, 0, 2000, .85f, 2, .35f, .72f, wave);
      for (long time : new long[] {300, 700, 1300}) {
        Bitmap transparent = Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888);
        EffectsPainter.draw(new Canvas(transparent), fx, 360, 720, 1, 70, time);
        List<EffectsPainter.Part> parts = EffectsPainter.parts(fx, 360, 720, 1, 70, time);
        assertFalse("no occupied region " + kind, parts.isEmpty());
        for (EffectsPainter.Part part : parts)
          assertFalse(
              "full-screen blocker", part.bounds.width() >= 359 && part.bounds.height() >= 719);
        int painted = 0;
        for (int y = 0; y < 720; y++)
          for (int x = 0; x < 360; x++)
            if (Color.alpha(transparent.getPixel(x, y)) > 20) {
              painted++;
              boolean covered = false;
              for (EffectsPainter.Part part : parts)
                if (part.bounds.contains(x + .5f, y + .5f)) {
                  covered = true;
                  break;
                }
              assertTrue("effect " + kind + " paints outside window at " + x + "," + y, covered);
            }
        assertTrue("invisible effect " + kind, painted > 0);
        if (time == 700) {
          Bitmap image = Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888);
          Canvas canvas = new Canvas(image);
          canvas.drawColor(0xfff6f7f8);
          canvas.drawBitmap(transparent, 0, 0, null);
          try (FileOutputStream out =
              new FileOutputStream(new File(folder, "effect-" + (kind + 1) + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
          }
        }
      }
    }
  }

  @Test
  public void particlesAreCappedAndToggleOffClearsThem() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication().getSharedPreferences("effects-test", 0);
    p.edit().putBoolean("fx_particles_top", true).apply();
    EffectsEngine engine = new EffectsEngine(p);
    for (int i = 0; i < 50; i++) engine.spawn(2, 1000, .9f, new float[48]);
    assertTrue(engine.items.size() <= 80);
    p.edit().putBoolean("fx_particles_top", false).apply();
    engine.update(new float[48], 1100, true, new float[48]);
    assertTrue(engine.items.isEmpty());
  }

  @Test
  @Config(qualifiers = "ru-rRU-w360dp-h800dp-xhdpi")
  public void effectsUiAndFullScreenPreviewStart() throws Exception {
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    activity.navigation.setSelectedItemId(MainActivity.NAV_EFFECTS);
    assertTrue(activity.navigation.getMenu().findItem(MainActivity.NAV_EFFECTS).isChecked());
    assertNotNull(activity.bpmStatus);
    android.view.View decor = activity.getWindow().getDecorView();
    decor.measure(
        android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY));
    decor.layout(0, 0, 720, 1600);
    Bitmap screen = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888);
    decor.draw(new Canvas(screen));
    File folder = new File("build/effects-checks");
    folder.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(folder, "settings.png"))) {
      screen.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    EffectsPreviewActivity preview =
        Robolectric.buildActivity(EffectsPreviewActivity.class).setup().get();
    assertNotNull(preview.getWindow().getDecorView());
  }

  @Test
  public void singleAcceleratedPassThroughSurfaceIsReusedAndRemoved() {
    Context c = RuntimeEnvironment.getApplication();
    SharedPreferences p = c.getSharedPreferences("overlay-test", 0);
    p.edit().putBoolean("fx_particles_top", true).apply();
    EffectsEngine engine = new EffectsEngine(p);
    engine.origin = 60;
    engine.spawn(2, android.os.SystemClock.uptimeMillis(), .5f, new float[48]);
    android.view.WindowManager manager =
        (android.view.WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    EffectsOverlay overlay = new EffectsOverlay(c, manager, engine);
    overlay.render();
    assertTrue(overlay.attached);
    android.view.View view = overlay.view;
    for (int i = 0; i < 30; i++) overlay.render();
    assertSame(view, overlay.view);
    android.view.WindowManager.LayoutParams lp =
        (android.view.WindowManager.LayoutParams) view.getLayoutParams();
    assertTrue((lp.flags & android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) != 0);
    assertTrue((lp.flags & android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
    overlay.close();
    assertFalse(overlay.attached);
    assertTrue(engine.items.isEmpty());
  }

  private android.widget.Button findButton(android.view.View view, String text) {
    if (view instanceof android.widget.Button
        && text.contentEquals(((android.widget.Button) view).getText()))
      return (android.widget.Button) view;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.widget.Button result = findButton(group.getChildAt(i), text);
        if (result != null) return result;
      }
    }
    return null;
  }
}

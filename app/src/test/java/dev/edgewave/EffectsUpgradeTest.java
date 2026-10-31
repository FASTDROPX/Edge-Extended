package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import android.view.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EffectsUpgradeTest {
  @Test
  public void sideParticlesCombineWithSideThicknessAndCanBeDisabled() {
    for (boolean enabled : new boolean[] {true, false}) {
      SharedPreferences p =
          RuntimeEnvironment.getApplication().getSharedPreferences("combo-" + enabled, 0);
      p.edit()
          .putBoolean("fx_particles_side", true)
          .putBoolean("fx_side", true)
          .putBoolean("fx_combos", enabled)
          .apply();
      EffectsEngine engine = new EffectsEngine(p);
      float[] data = new float[48];
      Arrays.fill(data, .4f);
      engine.update(data, 1000, true, new float[48]);
      Set<Integer> kinds = new HashSet<>();
      for (EffectsEngine.Fx f : engine.items) kinds.add(f.kind);
      if (!enabled) assertEquals(1, kinds.size());
      else assertTrue(kinds.size() >= 1 && kinds.size() <= 2);
    }
  }

  @Test
  public void particlesAndDetachedLineStayNearTheirOrigin() {
    for (int kind : new int[] {2, 3, 6}) {
      EffectsEngine.Fx f = new EffectsEngine.Fx(1, kind, 0, 1000, 1, 0, .5f, .99f, null);
      float[] point = EffectsPainter.particle(f, 360, 720, 1, 60, 999);
      if (kind == 3) assertTrue(point[0] < 50);
      else if (kind == 2) assertTrue(point[1] - 60 < 42);
      else assertTrue(720 - point[1] < 42);
    }
    float[] shape = new float[48];
    Arrays.fill(shape, 60);
    EffectsEngine.Fx line = new EffectsEngine.Fx(2, 0, 0, 1000, 1, 0, 0, 0, shape);
    List<EffectsPainter.Part> parts = EffectsPainter.parts(line, 360, 720, 1, 60, 999);
    assertTrue(parts.get(0).bounds.bottom < 90);
  }

  @Test
  public void trustedSurfaceIsBlackAcceleratedAndDoesNotCaptureTouches() {
    WindowManager.LayoutParams lp = OverlayWindows.scene(true);
    assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, lp.type);
    assertEquals(1, lp.alpha, 0);
    assertTrue((lp.flags & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) != 0);
    assertTrue((lp.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
  }

  @Test
  public void movingEdgesDoNotFadeWhileOnScreen() {
    float[] shape = new float[48];
    EffectsEngine.Fx fx = new EffectsEngine.Fx(1, 1, 0, 2000, .8f, 2, 0, 0, shape);
    for (long time : new long[] {200, 500, 800}) {
      Bitmap b = Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888);
      EffectsPainter.draw(new Canvas(b), fx, 360, 720, 1, 60, time);
      int max = 0;
      for (int y = 0; y < 720; y++)
        for (int x = 0; x < 360; x++) max = Math.max(max, Color.alpha(b.getPixel(x, y)));
      assertEquals(255, max);
    }
  }

  @Test
  public void smoothingPreparedOnceMatchesProfilesAtBandPositions() {
    float[] in = new float[48];
    for (int i = 0; i < 48; i++) in[i] = i / 48f;
    float[] prepared = new float[48];
    for (boolean classic : new boolean[] {false, true}) {
      WaveSmoothing.prepare(in, prepared, classic);
      for (int i = 0; i < 48; i++) {
        float a = i / 47f;
        float old =
            classic
                ? ClassicWaveProfile.sampleLayer(in, a, 1, 0)
                : WaveProfile.sampleLayer(in, a, 1, 0, .2f);
        float current =
            classic
                ? ClassicWaveProfile.samplePrepared(prepared, a, 1, 0)
                : WaveProfile.samplePrepared(prepared, a, 1, 0, .2f);
        assertEquals(old, current, .00001f);
      }
    }
  }
}

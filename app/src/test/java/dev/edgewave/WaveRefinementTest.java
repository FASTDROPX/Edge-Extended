package dev.edgewave;

import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "ru-rRU")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WaveRefinementTest {
  EffectsEngine.Fx fx(int kind) {
    float[] shape = new float[48];
    Arrays.fill(shape, 70);
    return new EffectsEngine.Fx(1, kind, 0, 2000, .8f, 0, .3f, .7f, shape);
  }

  Bitmap render(EffectsEngine.Fx fx, long time) {
    Bitmap b = Bitmap.createBitmap(360, 300, Bitmap.Config.ARGB_8888);
    EffectsPainter.draw(new Canvas(b), fx, 360, 300, 1, 70, time);
    return b;
  }

  float centerY(Bitmap b, int x) {
    float mass = 0, total = 0;
    for (int y = 0; y < b.getHeight(); y++) {
      int alpha = Color.alpha(b.getPixel(x, y));
      mass += y * alpha;
      total += alpha;
    }
    assertTrue(total > 0);
    return mass / total;
  }

  @Test
  public void duetOuterEdgesEraseTowardStationaryCenter() {
    assertEquals(0, MusicEffectsPainter.duetHead(0), 0);
    assertEquals(0, MusicEffectsPainter.duetTail(0), 0);
    assertEquals(1, MusicEffectsPainter.duetHead(.1f), .00001f);
    assertEquals(0, MusicEffectsPainter.duetTail(.1f), .00001f);
    assertEquals(1, MusicEffectsPainter.duetHead(.5f), 0);
    assertTrue(MusicEffectsPainter.duetTail(.5f) > .9f);
    assertEquals(1, MusicEffectsPainter.duetTail(1), 0);
    EffectsEngine.Fx f = fx(9);
    Bitmap b = render(f, 400);
    int left = 0, right = 0;
    for (int y = 0; y < 300; y++) {
      left += Color.alpha(b.getPixel(100, y));
      right += Color.alpha(b.getPixel(259, y));
    }
    assertEquals(left, right);
    assertEquals(0, Color.alpha(b.getPixel(20, 82)));
    assertTrue(Color.alpha(b.getPixel(150, 82)) > 0);
  }

  @Test
  public void duetUsesExponentialOutAcrossItsWholeMovement() {
    float last = 0, previousStep = 1;
    for (int i = 1; i <= 9; i++) {
      float p = MusicEffectsPainter.exponentialOut(i / 10f), step = p - last;
      assertTrue(step > 0);
      assertTrue(step < previousStep);
      previousStep = step;
      last = p;
    }
    assertEquals(1, MusicEffectsPainter.exponentialOut(1), 0);
  }

  @Test
  public void stringsKeepTheirSnapshotAndMoveAsOneForClearance() {
    SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("shape-sync", 0);
    EffectsEngine e = new EffectsEngine(p);
    float[] before = new float[48], after = new float[48];
    Arrays.fill(before, 60);
    Arrays.fill(after, 80);
    e.spawn(0, 1000, .8f, before);
    EffectsEngine.Fx trail = e.items.get(0);
    e.items.clear();
    e.spawn(8, 1000, .8f, before);
    e.syncShape(after);
    assertEquals(60, trail.shape[10], 0);
    assertEquals(60, e.items.get(0).shape[10], 0);
    assertEquals(20, e.items.get(0).clearance, 0);
  }

  @Test
  public void deletedEffectsAreNotAvailableEvenIfTheirOldKeysRemain() {
    SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("removed-fx", 0);
    p.edit()
        .putBoolean("fx_orbits", true)
        .putBoolean("fx_comet", true)
        .putBoolean("fx_drops", true)
        .apply();
    EffectsEngine e = new EffectsEngine(p);
    assertEquals(0, e.mask());
    assertEquals(10, EffectsEngine.COUNT);
    assertFalse(Arrays.asList(EffectsEngine.KEYS).contains("fx_drops"));
    assertFalse(Arrays.asList(EffectsEngine.KEYS).contains("fx_orbits"));
  }

  @Test
  public void stringsAndDuetCannotOverlapAcrossSeparateBeats() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication().getSharedPreferences("exclusion43", 0);
    EffectsEngine e = new EffectsEngine(p);
    e.spawn(8, 0, .8f, new float[48]);
    e.spawn(9, 100, .8f, new float[48]);
    assertEquals(1, e.items.size());
    assertFalse(ComboPlanner.compatible(8, 9));
    assertFalse(ComboPlanner.compatible(9, 8));
    e.items.clear();
    e.spawn(9, 0, .8f, new float[48]);
    e.spawn(8, 100, .8f, new float[48]);
    assertEquals(1, e.items.size());
  }

  @Test
  public void bottomPulseHasHeightAndMultipleLayers() {
    Bitmap b = render(fx(5), 600);
    java.util.Set<Integer> alpha = new java.util.HashSet<>();
    int top = 300;
    for (int y = 0; y < 300; y++)
      for (int x = 0; x < 360; x++) {
        int a = Color.alpha(b.getPixel(x, y));
        if (a > 40) {
          top = Math.min(top, y);
          alpha.add(a);
        }
      }
    assertTrue(top < 280);
    assertTrue(alpha.contains(255));
    int runs = 0;
    boolean inside = false;
    for (int y = 0; y < 300; y++) {
      boolean black = Color.alpha(b.getPixel(180, y)) > 220;
      if (black && !inside) runs++;
      inside = black;
    }
    assertTrue("bottom layers lost opaque black separation", runs >= 3);
  }

  @Test
  public void hybridClassicOscillationIsStrongerWithoutRaisingRest() {
    float[] active = new float[48], silent = new float[48];
    Arrays.fill(active, .3f);
    float a = HybridWaveProfile.samplePrepared(active, silent, .2f, 0, 0, 0, 0),
        b = HybridWaveProfile.samplePrepared(active, silent, .2f, 2, 0, 0, 0);
    float oldDelta =
        .8f
            * (ClassicWaveProfile.samplePrepared(active, .2f, 2, 0)
                - ClassicWaveProfile.samplePrepared(active, .2f, 0, 0));
    assertEquals(oldDelta * (1.25f / .8f), b - a, .00001f);
    assertEquals(
        .08f * .8f, HybridWaveProfile.samplePrepared(silent, silent, .2f, 0, 0, 0, 0), .00001f);
  }

  @Test
  public void iconLayerEdgesStaySeparatedAtEveryColumn() {
    Drawable icon =
        RuntimeEnvironment.getApplication().getDrawable(R.drawable.ic_launcher_foreground);
    Bitmap b = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888);
    icon.setBounds(0, 0, 216, 216);
    icon.draw(new Canvas(b));
    for (int x = 0; x < 216; x++) {
      int white = -1, mid = -1, back = -1;
      for (int y = 0; y < 216; y++) {
        int pixel = b.getPixel(x, y);
        if (pixel == Color.rgb(244, 244, 244)) white = y;
        if (pixel == Color.rgb(157, 157, 157)) mid = y;
        if (pixel == Color.rgb(83, 83, 83)) back = y;
      }
      assertTrue("front/middle touch at " + x, mid - white >= 5);
      assertTrue("middle/back touch at " + x, back - mid >= 5);
    }
  }
}
